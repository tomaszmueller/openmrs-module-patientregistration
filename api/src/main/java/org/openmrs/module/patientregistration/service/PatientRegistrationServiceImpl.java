package org.openmrs.module.patientregistration.service;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.LocationAttribute;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.Person;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.Visit;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.layout.web.address.AddressSupport;
import org.openmrs.module.emrapi.EmrApiProperties;
import org.openmrs.module.paperrecord.PaperRecordService;
import org.openmrs.module.paperrecord.UnableToPrintLabelException;
import org.openmrs.module.patientregistration.PatientRegistrationConstants;
import org.openmrs.module.patientregistration.PatientRegistrationGlobalProperties;
import org.openmrs.module.patientregistration.PatientRegistrationSearch;
import org.openmrs.module.patientregistration.PatientRegistrationUtil;
import org.openmrs.module.patientregistration.UserActivity;
import org.openmrs.module.patientregistration.service.db.PatientRegistrationDAO;
import org.openmrs.module.patientregistration.util.DuplicatePatient;
import org.openmrs.module.printer.Printer;
import org.openmrs.module.printer.PrinterService;
import org.openmrs.module.printer.PrinterType;
import org.openmrs.module.printer.UnableToPrintException;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


public class PatientRegistrationServiceImpl implements PatientRegistrationService {

	protected final Log log = LogFactory.getLog(getClass());

    private PrinterService printerService;

    private EmrApiProperties emrApiProperties;

	//***** PROPERTIES *****
	private PatientRegistrationDAO dao;

    private PaperRecordService paperRecordService;

    //***** GETTERS AND SETTERS ****
	
	public PatientRegistrationDAO getDao() {
		return dao;
	}

	public void setDao(PatientRegistrationDAO dao) {
		this.dao = dao;
	}

    public void setPrinterService(PrinterService printerService) {
        this.printerService = printerService;
    }

    public void setEmrApiProperties(EmrApiProperties emrApiProperties) {
        this.emrApiProperties = emrApiProperties;
    }

    public void setPaperRecordService(PaperRecordService paperRecordService) {
        this.paperRecordService = paperRecordService;
    }

    //***** SERVICE METHODS ***********
	@Transactional
	public Encounter registerPatient(Patient patient, Person provider, EncounterType encounterType, Location location, Date registrationDate) {
		
		if (patient == null) {
			throw new APIException("No patient specified");
		}
		if (provider == null) {
			throw new APIException("No provider specified");
		}
		if (encounterType == null) {
			throw new APIException("No encounter type specified");
		}
		if (location == null) {
			throw new APIException("No location specified");
		}
		if (registrationDate == null) {
			throw new APIException("No registration date specified");
		}
		
        Encounter registration = getEncounterByDateAndType(patient, encounterType, location, registrationDate);
        if(registration==null){
            registration = new Encounter();
            registration.setPatient(patient);
            registration.setProvider(provider);
            registration.setEncounterType(encounterType);
            registration.setLocation(location);
            registration.setEncounterDatetime(registrationDate);

        }else{
            log.info("patient " + patient.getId() + " already registered on " + registrationDate + " at " + location.getName());
            if(registrationDate!=null && registration.getEncounterDatetime().compareTo(registrationDate)!=0){
                registration.setEncounterDatetime(registrationDate);
                Visit visit = registration.getVisit();
                if(visit!=null){
                    if((OpenmrsUtil.compare(registrationDate, visit.getStartDatetime()) < 0) ||
                            (OpenmrsUtil.compareWithNullAsEarliest(registrationDate, visit.getStopDatetime()) > 0)){
                        //the new registrationDate falls out of the visit boundaries
                        registration.setVisit(null);
                    }
                }
            }
        }
        Context.getEncounterService().saveEncounter(registration);
		return registration;
    }

	//@Transactional
	public Encounter registerPatient(Patient patient, Person provider, EncounterType encounterType, Location location) {
		// delegate to main register patient, using the current date as the registration date
	   return registerPatient(patient, provider, encounterType, location, new Date());
    }

	@Transactional(readOnly=true)
	public List<Patient> exactSearch(PersonName personName) {
		// return null if passed null
		if (personName == null) {
			return null;
		}
	    return dao.getPatientsByName(personName);
		//return getPatientRegistrationSearch().exactSearch(personName);
    }
	
	@Transactional(readOnly=true)
	public List<Patient> search(PersonName personName) {
		// return null if passed null
		if (personName == null) {
			return null;
		}
	
		return getPatientRegistrationSearch().search(personName);
    }
	
	@Transactional(readOnly=true)
	public List<Patient> search(String personFirstName) {
		// return null if blank value was passed
		if(!StringUtils.isNotBlank(personFirstName)){
			return null;
		}
	
		return getPatientRegistrationSearch().search(personFirstName);
    }
	@Transactional(readOnly=true)
	public Set<String> searchNames(String name, String nameField) {
		return dao.searchNames(name, nameField);
	}
	@Transactional(readOnly=true)
	public Map<String,Integer> searchNamesByOccurence(String name, String nameField) {
		return dao.searchNamesByOccurence(name, nameField);
	}
	@Transactional(readOnly=true)
	public List<Integer> getUnknownPersonId() {
		return dao.getUnknownPersonId();
	}
	@Transactional(readOnly=true)
	public List<Integer> getPhoneticsPersonId(String firstName, String lastName) {
		return dao.getPhoneticsPersonId(firstName, lastName);
	}
	@Transactional(readOnly=true)
	public List<Patient> getPatientsByNameId(List<Integer> nameIds) {
		return dao.getPatientsByNameId(nameIds);
	}
	
	@Transactional(readOnly=true)
	public List<String> getDistinctObs(Integer conceptId){
		return dao.getDistinctObs(conceptId);
	}
	
	@Transactional(readOnly=true)
	public Set<Integer> getDistinctDuplicateObs(Integer conceptId){
		return dao.getDistinctDuplicateObs(conceptId);
	}
	
	@Transactional(readOnly=true)
	public List<Patient> search(Patient patient) {
		// return null if passed null
		if (patient == null) {
			return null;
		}
		
		return getPatientRegistrationSearch().search(patient);
    }

    @Transactional(readOnly=true)
    public void printPaperRecordLabel(Patient patient, Location location, Integer count)
            throws UnableToPrintLabelException {

        paperRecordService.printPaperRecordLabels(patient, location, count);
    }

    @Transactional(readOnly=true)
    public void printPaperFormLabel(Patient patient, Location location, Integer count)
            throws UnableToPrintLabelException {

        paperRecordService.printPaperFormLabels(patient, location, count);
    }

    @Transactional(readOnly=true)
    public void printIDCardLabel(Patient patient, Location location)
        throws UnableToPrintLabelException {

       paperRecordService.printIdCardLabel(patient, location);
    }

    @Transactional(readOnly = true)
    public void printIDCard(Patient patient, Location location)
            throws UnableToPrintException, UnableToPrintLabelException {

        Location issuingLocation = PatientRegistrationUtil.getMedicalRecordLocationRecursivelyBasedOnTag(location);

        // now get the default printer
        Printer printer = printerService.getDefaultPrinter(location, PrinterType.ID_CARD);

        if (printer == null) {
            throw new UnableToPrintLabelException("No default printer specified for location " + location + ". Please contact your system administrator.");
        }

        printerService.print(generateParametersForPrintTemplate(patient, issuingLocation), printer, true);
    }

    private Map<String,Object> generateParametersForPrintTemplate(Patient patient, Location issuingLocation) {

        Map<String, Object> paramMap = new HashMap<String, Object>();
        DateFormat df = new SimpleDateFormat(PatientRegistrationConstants.DATE_FORMAT_DISPLAY, Context.getLocale());

        paramMap.put("name", (patient.getPersonName().getFamilyName() != null ? patient.getPersonName().getFamilyName() : "") + ", "
                + (patient.getPersonName().getGivenName() != null ? patient.getPersonName().getGivenName() : "") + " ");

        PatientIdentifier identifier = PatientRegistrationUtil.getPreferredIdentifier(patient);
        paramMap.put("patientIdentifier", identifier != null ? identifier.getIdentifier() : "");

        paramMap.put("gender", patient.getGender());
        paramMap.put("birthdate", df.format(patient.getBirthdate()));
        paramMap.put("birthdateEstimated", patient.getBirthdateEstimated());

        paramMap.put("issuingLocation", getNameToPrintOnIdCard(issuingLocation).toString());
        paramMap.put("issuedDate", df.format(new Date()));

        PersonAttributeType type = PatientRegistrationGlobalProperties.GLOBAL_PROPERTY_ID_CARD_PERSON_ATTRIBUTE_TYPE();
        if (type != null) {
            PersonAttribute attr = patient.getAttribute(type);
            paramMap.put("telephoneNumber", (attr != null ? attr.getValue() : "")) ;
        }

        if (PatientRegistrationGlobalProperties.GLOBAL_PROPERTY_ID_CARD_LABEL_TEXT() != null) {
            paramMap.put("customCardLabel", PatientRegistrationGlobalProperties.GLOBAL_PROPERTY_ID_CARD_LABEL_TEXT());
        }

        if (patient.getPersonAddress() != null) {
            paramMap.put("addressLines", generateAddressLines(patient.getPersonAddress()));
        }

        return paramMap;
    }

    private List<String> generateAddressLines(PersonAddress personAddress) {

        List<String> addressLines = new ArrayList<String>();

        if (AddressSupport.getInstance().getDefaultLayoutTemplate() != null && AddressSupport.getInstance().getDefaultLayoutTemplate().getLines() != null) {

            List<List<Map<String,String>>> lines = AddressSupport.getInstance().getDefaultLayoutTemplate().getLines();
            ListIterator<List<Map<String,String>>> iter = lines.listIterator();

            // iterate through each line in the template
            while(iter.hasNext()){
                List<Map<String,String>> line = iter.next();
                // now iterate through all the tokens in the line and build the string to print
                StringBuffer output = new StringBuffer();
                for (Map<String,String> token : line) {
                    // find all the tokens on this line, and then add them to that output line
                    if(token.get("isToken").equals(AddressSupport.getInstance().getDefaultLayoutTemplate().getLayoutToken())) {

                        String property = PatientRegistrationUtil.getPersonAddressProperty(personAddress, token.get("codeName"));

                        if (!StringUtils.isBlank(property)) {
                            output.append(property + ", ");
                        }
                    }
                }

                if (output.length() > 2) {
                    // drop the trailing comma and space from the last token on the line
                    output.replace(output.length() - 2, output.length(), "");
                }

                if (!StringUtils.isBlank(output.toString())) {
                    addressLines.add(output.toString());
                }
            }
        }
        else {
            log.error("Address template not properly configured");
        }

        return addressLines;
    }

    protected String getNameToPrintOnIdCard(Location location) {

        List<LocationAttribute> nameToPrintOnIdCard = location.getActiveAttributes(PatientRegistrationGlobalProperties.getLocationAttributeTypeNameToPrintOnIdCard());

        if (nameToPrintOnIdCard != null && nameToPrintOnIdCard.size() > 0) {
            // there should never be more for than one specified name to print on the id card--max allowed for this attribute = 1
            return (String) nameToPrintOnIdCard.get(0).getValue();
        }
        else {
            return location.getDisplayString();
        }
    }


    @Transactional(readOnly=true)
	public List<Patient> removeUnknownPatients(List<Patient> patientList){
		List<Patient> filteredPatientList = null;
		if(patientList!=null && patientList.size()>0){
			filteredPatientList = new ArrayList<Patient>();
			PersonAttributeType unknownPatientAttributeType = PatientRegistrationGlobalProperties.UNKNOWN_PATIENT_PERSON_ATTRIBUTE_TYPE();
			if(unknownPatientAttributeType!=null){
				for(Patient patient : patientList){
					PersonAttribute att = patient.getAttribute(unknownPatientAttributeType);
					if(att==null || (att!=null && !StringUtils.equals(att.getValue(), "true"))){
						filteredPatientList.add(patient);
					}					
				}
			}else{
				filteredPatientList = patientList;
			}
		}
		
		return filteredPatientList;
	}

    /**
     * @see PatientRegistrationService#getNumberOfRegistrationEncounters(List<EncounterType>, Date, Date)
     */
	// TODO: do we want to move this to the DAO?
	@Transactional(readOnly=true)
	public Map<EncounterType, Integer> getNumberOfRegistrationEncounters(List<EncounterType> encounterTypes, Location location, Date fromDate, Date toDate) {
		
		Map<EncounterType, Integer> m = new HashMap<EncounterType, Integer>();
		
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		
		// Get all of the Encounter Types by ID
		Map<String, EncounterType> encounterTypeMap = new HashMap<String, EncounterType>();
		if (encounterTypes == null) {
			encounterTypes = Context.getEncounterService().getAllEncounterTypes();
		}
		for (EncounterType type : encounterTypes) {
			encounterTypeMap.put(type.getEncounterTypeId().toString(), type);
		}
		
		// Get the number of registrations grouped by encounter type
		StringBuilder query = new StringBuilder();
		query.append("select e.encounter_type, count(*) from encounter e, patient p ");
		query.append("where e.patient_id = p.patient_id ");
		query.append("and e.voided = 0 and p.voided = 0 ");
		query.append("and e.encounter_type in (" + OpenmrsUtil.join(encounterTypeMap.keySet(), ",") + ") ");
		if (fromDate != null) {
			query.append("and date(e.encounter_datetime) >= " + df.format(fromDate) + " ");
		}
		if (toDate != null) {
			query.append("and date(e.encounter_datetime) <= " + df.format(toDate) + " ");
		}
		if (location != null) {
			query.append("and e.location_id = " + location.getId() + " ");
		}
		query.append("group by e.encounter_type");
		
		List<List<Object>> queryResults = Context.getAdministrationService().executeSQL(query.toString(), true);
		for (List<Object> l : queryResults) {
			Object encounterTypeId = l.get(0);
			if (encounterTypeId != null) {			
				EncounterType type = encounterTypeMap.get(encounterTypeId.toString());
				Integer count = Integer.valueOf(l.get(1).toString());
				m.put(type, count);
			}
		}

		return m;
	}
	
    /**
     * @see PatientRegistrationService#getNumberOfEncountersByDate(EncounterType, Location)
     */
	// TODO: do we want to move this to the DAO?
	@Transactional(readOnly=true)
	public Map<Date, Integer> getNumberOfEncountersByDate(EncounterType encounterType, Location location) {
		
		Map<Date, Integer> m = new TreeMap<Date, Integer>();
		try{
			Context.addProxyPrivilege(OpenmrsConstants.PRIV_SQL_LEVEL_ACCESS);
			// Get the number of registrations grouped by encounter type
			StringBuilder query = new StringBuilder();
			query.append("select date(e.encounter_datetime) as encounter_date, count(*) from encounter e, patient p ");
			query.append("where e.patient_id = p.patient_id ");
			query.append("and e.voided = 0 and p.voided = 0 ");
			if (encounterType != null) {
				query.append("and e.encounter_type = " + encounterType.getEncounterTypeId() + " ");
			}
			if (location != null) {
				query.append("and e.location_id = " + location.getLocationId() + " ");
			}
			query.append("group by date(e.encounter_datetime)");
			
			List<List<Object>> queryResults = Context.getAdministrationService().executeSQL(query.toString(), true);
			for (List<Object> l : queryResults) {
				m.put((Date)l.get(0), Integer.valueOf(l.get(1).toString()));
			}
		}finally {
			Context.removeProxyPrivilege(OpenmrsConstants.PRIV_SQL_LEVEL_ACCESS);
		}
		return m;
	}

    /**
     * @see PatientRegistrationService#getNumberOfPatientsByAddress(Map<String, String>, String, EncounterType, Location)
     */
	@Transactional(readOnly=true)
	public Map<String, Integer> getNumberOfPatientsByAddress(Map<String, String> filterCriteria, String addressField, EncounterType encounterType, Location location) {
		return dao.getNumberOfRegistrationsByAddress(filterCriteria, addressField, encounterType, location, true);
	}

	@Transactional(readOnly=true)
	public List<DuplicatePatient> getDuplicatePatients(Patient patient){
		return dao.getDuplicatePatients(patient);
	}
    /**
     * @see PatientRegistrationService#getNumberOfEncountersByAddress(Map<String, String>, String, EncounterType, Location)
     */
	@Transactional(readOnly=true)
	public Map<String, Integer> getNumberOfEncountersByAddress(Map<String, String> filterCriteria, String addressField, EncounterType encounterType, Location location) {
		return dao.getNumberOfRegistrationsByAddress(filterCriteria, addressField, encounterType, location, false);
	}

    /**
     * @see PatientRegistrationService#saveUserActivity(UserActivity)
     */
    @Transactional
    public UserActivity saveUserActivity(UserActivity userActivity) {
        return dao.saveUserActivity(userActivity);
    }

    @Transactional(readOnly = true)
    public List<Obs> getPatientObs(Patient patient, EncounterType encounterType,  List<Encounter> encounters, List<Concept> questions, Location location, Date registrationDate) {

        // clear the time component to get the start time to search (first millisecond of current day)
        Date startTime = PatientRegistrationUtil.clearTimeComponent(registrationDate);

        // create the end time to search (last millisecond of the current day)
        Calendar cal = Calendar.getInstance();
        cal.setTime(startTime);
        cal.add(Calendar.DAY_OF_MONTH, +1);
        cal.add(Calendar.MILLISECOND, -1);
        Date endTime = cal.getTime();

        List<Obs> obs= Context.getObsService().getObservations(Collections.singletonList((Person)patient)
                , encounters, questions
                , null, null, Collections.singletonList((Location)location)
                , null, null, null, startTime, endTime, false);

        return (obs != null && obs.size() > 0 ? obs : null);
    }



    /**
    * Private utility methods
    */
	
	/**
	 * Test if a patient is already registered at the given location on the given date
	 * 
	 * @param patient the patient
	 * @param encounterType the encounter type of the registration
	 * @param location the location of the registration
	 * @param registrationDate the date of registration
	 * @return true/false if the patient is already registered at the given location on the given date
	 */
	private Boolean alreadyRegistered(Patient patient, EncounterType encounterType, Location location, Date registrationDate) {
		
		Encounter encounter = getEncounterByDateAndType(patient, encounterType, location, registrationDate);
		return (encounter != null ? true : false);
	}
	
	private Encounter getEncounterByDateAndType(Patient patient, EncounterType encounterType, Location location, Date registrationDate) {
		
		Date startTime = null;
		Date endTime = null;
		if(registrationDate!=null){
			// clear the time component to get the start time to search (first millisecond of current day)
			startTime = PatientRegistrationUtil.clearTimeComponent(registrationDate);
			
			// create the end time to search (last millisecond of the current day)
			Calendar cal = Calendar.getInstance();
			cal.setTime(startTime);
			cal.add(Calendar.DAY_OF_MONTH, +1);
			cal.add(Calendar.MILLISECOND, -1);
			endTime = cal.getTime();
		}		
		List<Encounter> encounters= Context.getEncounterService().getEncounters(patient, location, null, null, null, Arrays.asList(encounterType), null, null, null, false);
		if(encounters!=null && encounters.size()>0){			
			int maxIndex= encounters.size()-1;
			//return the most recent encounter
			return encounters.get(maxIndex);
		}else{
			return null;
		}
		
	}
    @Override
    public Encounter getFirstEncounterByType(Patient patient,
                                             EncounterType encounterType, Location location) {
        List<Encounter> encounters = Context.getEncounterService().getEncounters(patient, location, null, null, null, Arrays.asList(encounterType), null, false);
        if(encounters!=null && encounters.size()>0){
            return encounters.get(0);
        }else{
            return null;
        }
    }

    @Override
    public Encounter getLastEncounterByType(Patient patient,
                                             EncounterType encounterType, Location location) {
        List<Encounter> encounters = Context.getEncounterService().getEncounters(patient, location, null, null, null, Arrays.asList(encounterType), null, false);
        if(encounters!=null && encounters.size()>0){
            int maxIndex= encounters.size()-1;
            //return the most recent encounter
            return encounters.get(maxIndex);
        }else{
            return null;
        }
    }
	/**
	 * Fetches and loads the Patient Search Class specified in GLOBAL_PROPERTY_SEARCH_CLASS
	 * If no search class defined in a global property, use the default search class
	 */
    private PatientRegistrationSearch getPatientRegistrationSearch() {
		String patientSearchClass = PatientRegistrationGlobalProperties.GLOBAL_PROPERTY_SEARCH_CLASS();
		
		// if the global prop doesn't exist, use the hard-coded default class
		if (StringUtils.isBlank(patientSearchClass)) {
			patientSearchClass = PatientRegistrationConstants.DEFAULT_SEARCH_CLASS;
		}
		 
		// now try to load the specified class
		@SuppressWarnings("rawtypes")
        Class patientSearch;
		try {
	        patientSearch= Context.loadClass(patientSearchClass);
        }
        catch (ClassNotFoundException e) {
	        throw new APIException("Unable to load search class " + patientSearchClass);
        }
		
        try {
        	return (PatientRegistrationSearch) patientSearch.newInstance();
        }
        catch(Exception e) {
        	throw new APIException("Unable to instantiate search class " + patientSearchClass);
        }	
	}
}
