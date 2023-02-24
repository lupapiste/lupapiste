
package lupapalvelu.tiedonohjaus;

import java.math.BigInteger;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the lupapalvelu.tiedonohjaus package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _Sent_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Sent");
    private final static QName _AcceptionDescription_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "AcceptionDescription");
    private final static QName _Version_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Version");
    private final static QName _Name_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Name");
    private final static QName _DeliveryDate_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "DeliveryDate");
    private final static QName _ProtectionLevel_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "ProtectionLevel");
    private final static QName _Email_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Email");
    private final static QName _PublicityClass_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "PublicityClass");
    private final static QName _HashValue_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "HashValue");
    private final static QName _File_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "File");
    private final static QName _FunctionClassification_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "FunctionClassification");
    private final static QName _Spatial_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Spatial");
    private final static QName _Role_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Role");
    private final static QName _Requires_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Requires");
    private final static QName _IsPartOf_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "IsPartOf");
    private final static QName _AccessRight_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "AccessRight");
    private final static QName _StorageLocation_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "StorageLocation");
    private final static QName _ElectronicNotification_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "ElectronicNotification");
    private final static QName _SecurityReason_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "SecurityReason");
    private final static QName _Record_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Record");
    private final static QName _Language_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Language");
    private final static QName _Issued_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Issued");
    private final static QName _HasRedaction_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "HasRedaction");
    private final static QName _Function_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Function");
    private final static QName _ElectronicId_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "ElectronicId");
    private final static QName _HasFormat_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "HasFormat");
    private final static QName _Format_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Format");
    private final static QName _OtherId_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "OtherId");
    private final static QName _Source_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Source");
    private final static QName _AcceptionDate_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "AcceptionDate");
    private final static QName _Acquired_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Acquired");
    private final static QName _Action_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Action");
    private final static QName _Modified_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Modified");
    private final static QName _IsRedactionOf_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "IsRedactionOf");
    private final static QName _SecurityPeriod_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "SecurityPeriod");
    private final static QName _References_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "References");
    private final static QName _RetentionReason_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "RetentionReason");
    private final static QName _Restriction_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Restriction");
    private final static QName _ProtectionClass_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "ProtectionClass");
    private final static QName _RetentionPeriod_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "RetentionPeriod");
    private final static QName _Subject_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Subject");
    private final static QName _Date_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Date");
    private final static QName _ContactInformation_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "ContactInformation");
    private final static QName _SecurityClass_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "SecurityClass");
    private final static QName _Ssn_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Ssn");
    private final static QName _AccessRightDescription_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "AccessRightDescription");
    private final static QName _Title_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Title");
    private final static QName _Coverage_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Coverage");
    private final static QName _IsReferencedBy_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "IsReferencedBy");
    private final static QName _Organisation_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Organisation");
    private final static QName _Checker_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Checker");
    private final static QName _TransferContractId_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "TransferContractId");
    private final static QName _RetentionPeriodEnd_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "RetentionPeriodEnd");
    private final static QName _Available_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Available");
    private final static QName _HasVersion_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "HasVersion");
    private final static QName _MainFunction_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "MainFunction");
    private final static QName _Rights_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Rights");
    private final static QName _Document_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Document");
    private final static QName _ContactPerson_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "ContactPerson");
    private final static QName _SubFunction_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "SubFunction");
    private final static QName _Encryption_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Encryption");
    private final static QName _Address_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Address");
    private final static QName _Owner_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Owner");
    private final static QName _Person_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Person");
    private final static QName _Valid_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Valid");
    private final static QName _Jurisdiction_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Jurisdiction");
    private final static QName _Accepted_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Accepted");
    private final static QName _FunctionCode_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "FunctionCode");
    private final static QName _Audience_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Audience");
    private final static QName _IsReplacedBy_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "IsReplacedBy");
    private final static QName _UseType_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "UseType");
    private final static QName _Status_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Status");
    private final static QName _Agent_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Agent");
    private final static QName _Finished_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Finished");
    private final static QName _Temporal_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Temporal");
    private final static QName _IsFormatOf_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "IsFormatOf");
    private final static QName _HasPart_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "HasPart");
    private final static QName _Created_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Created");
    private final static QName _TransferInformation_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "TransferInformation");
    private final static QName _SecurityPeriodEnd_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "SecurityPeriodEnd");
    private final static QName _MediumID_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "MediumID");
    private final static QName _Description_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Description");
    private final static QName _HashAlgorithm_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "HashAlgorithm");
    private final static QName _PhoneNumber_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "PhoneNumber");
    private final static QName _NativeId_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "NativeId");
    private final static QName _NotificationPeriod_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "NotificationPeriod");
    private final static QName _ArrivalDate_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "ArrivalDate");
    private final static QName _Authenticity_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Authenticity");
    private final static QName _Gathered_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Gathered");
    private final static QName _Type_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Type");
    private final static QName _PersonalData_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "PersonalData");
    private final static QName _AlternativeTitle_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "AlternativeTitle");
    private final static QName _IsVersionOf_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "IsVersionOf");
    private final static QName _CorporateName_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "CorporateName");
    private final static QName _Replaces_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Replaces");
    private final static QName _IsRequiredBy_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "IsRequiredBy");
    private final static QName _Delivered_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Delivered");
    private final static QName _Abstract_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Abstract");
    private final static QName _MetadataSchema_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "MetadataSchema");
    private final static QName _Path_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "Path");
    private final static QName _SignatureDescription_QNAME = new QName("http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", "SignatureDescription");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: lupapalvelu.tiedonohjaus
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link SubFunctionType }
     * 
     */
    public SubFunctionType createSubFunctionType() {
        return new SubFunctionType();
    }

    /**
     * Create an instance of {@link DocumentType }
     * 
     */
    public DocumentType createDocumentType() {
        return new DocumentType();
    }

    /**
     * Create an instance of {@link ContactPersonType }
     * 
     */
    public ContactPersonType createContactPersonType() {
        return new ContactPersonType();
    }

    /**
     * Create an instance of {@link MainFunctionType }
     * 
     */
    public MainFunctionType createMainFunctionType() {
        return new MainFunctionType();
    }

    /**
     * Create an instance of {@link AgentType }
     * 
     */
    public AgentType createAgentType() {
        return new AgentType();
    }

    /**
     * Create an instance of {@link TimePeriodType }
     * 
     */
    public TimePeriodType createTimePeriodType() {
        return new TimePeriodType();
    }

    /**
     * Create an instance of {@link PersonType }
     * 
     */
    public PersonType createPersonType() {
        return new PersonType();
    }

    /**
     * Create an instance of {@link TransferInformationType }
     * 
     */
    public TransferInformationType createTransferInformationType() {
        return new TransferInformationType();
    }

    /**
     * Create an instance of {@link RecordRelation }
     * 
     */
    public RecordRelation createRecordRelation() {
        return new RecordRelation();
    }

    /**
     * Create an instance of {@link Custom }
     * 
     */
    public Custom createCustom() {
        return new Custom();
    }

    /**
     * Create an instance of {@link ActionEvent }
     * 
     */
    public ActionEvent createActionEvent() {
        return new ActionEvent();
    }

    /**
     * Create an instance of {@link AuthenticityType }
     * 
     */
    public AuthenticityType createAuthenticityType() {
        return new AuthenticityType();
    }

    /**
     * Create an instance of {@link ClassificationScheme }
     * 
     */
    public ClassificationScheme createClassificationScheme() {
        return new ClassificationScheme();
    }

    /**
     * Create an instance of {@link FunctionClassificationType }
     * 
     */
    public FunctionClassificationType createFunctionClassificationType() {
        return new FunctionClassificationType();
    }

    /**
     * Create an instance of {@link FileType }
     * 
     */
    public FileType createFileType() {
        return new FileType();
    }

    /**
     * Create an instance of {@link ActionType }
     * 
     */
    public ActionType createActionType() {
        return new ActionType();
    }

    /**
     * Create an instance of {@link CaseFileRelation }
     * 
     */
    public CaseFileRelation createCaseFileRelation() {
        return new CaseFileRelation();
    }

    /**
     * Create an instance of {@link FormatType }
     * 
     */
    public FormatType createFormatType() {
        return new FormatType();
    }

    /**
     * Create an instance of {@link ElectronicNotificationType }
     * 
     */
    public ElectronicNotificationType createElectronicNotificationType() {
        return new ElectronicNotificationType();
    }

    /**
     * Create an instance of {@link RecordType }
     * 
     */
    public RecordType createRecordType() {
        return new RecordType();
    }

    /**
     * Create an instance of {@link AccessRightType }
     * 
     */
    public AccessRightType createAccessRightType() {
        return new AccessRightType();
    }

    /**
     * Create an instance of {@link OrganisationType }
     * 
     */
    public OrganisationType createOrganisationType() {
        return new OrganisationType();
    }

    /**
     * Create an instance of {@link CoverageType }
     * 
     */
    public CoverageType createCoverageType() {
        return new CoverageType();
    }

    /**
     * Create an instance of {@link SubjectType }
     * 
     */
    public SubjectType createSubjectType() {
        return new SubjectType();
    }

    /**
     * Create an instance of {@link ContactInformationType }
     * 
     */
    public ContactInformationType createContactInformationType() {
        return new ContactInformationType();
    }

    /**
     * Create an instance of {@link RestrictionType }
     * 
     */
    public RestrictionType createRestrictionType() {
        return new RestrictionType();
    }

    /**
     * Create an instance of {@link CaseFile }
     * 
     */
    public CaseFile createCaseFile() {
        return new CaseFile();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Sent")
    public JAXBElement<XMLGregorianCalendar> createSent(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_Sent_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "AcceptionDescription")
    public JAXBElement<String> createAcceptionDescription(String value) {
        return new JAXBElement<String>(_AcceptionDescription_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Version")
    public JAXBElement<String> createVersion(String value) {
        return new JAXBElement<String>(_Version_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Name")
    public JAXBElement<String> createName(String value) {
        return new JAXBElement<String>(_Name_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "DeliveryDate")
    public JAXBElement<XMLGregorianCalendar> createDeliveryDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_DeliveryDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ProtectionLevelType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "ProtectionLevel")
    public JAXBElement<ProtectionLevelType> createProtectionLevel(ProtectionLevelType value) {
        return new JAXBElement<ProtectionLevelType>(_ProtectionLevel_QNAME, ProtectionLevelType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Email")
    public JAXBElement<String> createEmail(String value) {
        return new JAXBElement<String>(_Email_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PublicityClassType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "PublicityClass")
    public JAXBElement<PublicityClassType> createPublicityClass(PublicityClassType value) {
        return new JAXBElement<PublicityClassType>(_PublicityClass_QNAME, PublicityClassType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "HashValue")
    public JAXBElement<String> createHashValue(String value) {
        return new JAXBElement<String>(_HashValue_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FileType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "File")
    public JAXBElement<FileType> createFile(FileType value) {
        return new JAXBElement<FileType>(_File_QNAME, FileType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FunctionClassificationType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "FunctionClassification")
    public JAXBElement<FunctionClassificationType> createFunctionClassification(FunctionClassificationType value) {
        return new JAXBElement<FunctionClassificationType>(_FunctionClassification_QNAME, FunctionClassificationType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Spatial")
    public JAXBElement<String> createSpatial(String value) {
        return new JAXBElement<String>(_Spatial_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Role")
    public JAXBElement<String> createRole(String value) {
        return new JAXBElement<String>(_Role_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Requires")
    public JAXBElement<String> createRequires(String value) {
        return new JAXBElement<String>(_Requires_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "IsPartOf")
    public JAXBElement<String> createIsPartOf(String value) {
        return new JAXBElement<String>(_IsPartOf_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AccessRightType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "AccessRight")
    public JAXBElement<AccessRightType> createAccessRight(AccessRightType value) {
        return new JAXBElement<AccessRightType>(_AccessRight_QNAME, AccessRightType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "StorageLocation")
    public JAXBElement<String> createStorageLocation(String value) {
        return new JAXBElement<String>(_StorageLocation_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ElectronicNotificationType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "ElectronicNotification")
    public JAXBElement<ElectronicNotificationType> createElectronicNotification(ElectronicNotificationType value) {
        return new JAXBElement<ElectronicNotificationType>(_ElectronicNotification_QNAME, ElectronicNotificationType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "SecurityReason")
    public JAXBElement<String> createSecurityReason(String value) {
        return new JAXBElement<String>(_SecurityReason_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RecordType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Record")
    public JAXBElement<RecordType> createRecord(RecordType value) {
        return new JAXBElement<RecordType>(_Record_QNAME, RecordType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Language")
    public JAXBElement<String> createLanguage(String value) {
        return new JAXBElement<String>(_Language_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Issued")
    public JAXBElement<XMLGregorianCalendar> createIssued(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_Issued_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "HasRedaction")
    public JAXBElement<String> createHasRedaction(String value) {
        return new JAXBElement<String>(_HasRedaction_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Function")
    public JAXBElement<String> createFunction(String value) {
        return new JAXBElement<String>(_Function_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "ElectronicId")
    public JAXBElement<String> createElectronicId(String value) {
        return new JAXBElement<String>(_ElectronicId_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "HasFormat")
    public JAXBElement<String> createHasFormat(String value) {
        return new JAXBElement<String>(_HasFormat_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FormatType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Format")
    public JAXBElement<FormatType> createFormat(FormatType value) {
        return new JAXBElement<FormatType>(_Format_QNAME, FormatType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "OtherId")
    public JAXBElement<String> createOtherId(String value) {
        return new JAXBElement<String>(_OtherId_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Source")
    public JAXBElement<String> createSource(String value) {
        return new JAXBElement<String>(_Source_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "AcceptionDate")
    public JAXBElement<XMLGregorianCalendar> createAcceptionDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_AcceptionDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Acquired")
    public JAXBElement<XMLGregorianCalendar> createAcquired(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_Acquired_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ActionType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Action")
    public JAXBElement<ActionType> createAction(ActionType value) {
        return new JAXBElement<ActionType>(_Action_QNAME, ActionType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Modified")
    public JAXBElement<XMLGregorianCalendar> createModified(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_Modified_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "IsRedactionOf")
    public JAXBElement<String> createIsRedactionOf(String value) {
        return new JAXBElement<String>(_IsRedactionOf_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link BigInteger }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "SecurityPeriod")
    public JAXBElement<BigInteger> createSecurityPeriod(BigInteger value) {
        return new JAXBElement<BigInteger>(_SecurityPeriod_QNAME, BigInteger.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "References")
    public JAXBElement<String> createReferences(String value) {
        return new JAXBElement<String>(_References_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "RetentionReason")
    public JAXBElement<String> createRetentionReason(String value) {
        return new JAXBElement<String>(_RetentionReason_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RestrictionType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Restriction")
    public JAXBElement<RestrictionType> createRestriction(RestrictionType value) {
        return new JAXBElement<RestrictionType>(_Restriction_QNAME, RestrictionType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "ProtectionClass")
    public JAXBElement<String> createProtectionClass(String value) {
        return new JAXBElement<String>(_ProtectionClass_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link BigInteger }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "RetentionPeriod")
    public JAXBElement<BigInteger> createRetentionPeriod(BigInteger value) {
        return new JAXBElement<BigInteger>(_RetentionPeriod_QNAME, BigInteger.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SubjectType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Subject")
    public JAXBElement<SubjectType> createSubject(SubjectType value) {
        return new JAXBElement<SubjectType>(_Subject_QNAME, SubjectType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Date")
    public JAXBElement<XMLGregorianCalendar> createDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_Date_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ContactInformationType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "ContactInformation")
    public JAXBElement<ContactInformationType> createContactInformation(ContactInformationType value) {
        return new JAXBElement<ContactInformationType>(_ContactInformation_QNAME, ContactInformationType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SecurityClassType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "SecurityClass")
    public JAXBElement<SecurityClassType> createSecurityClass(SecurityClassType value) {
        return new JAXBElement<SecurityClassType>(_SecurityClass_QNAME, SecurityClassType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Ssn")
    public JAXBElement<String> createSsn(String value) {
        return new JAXBElement<String>(_Ssn_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "AccessRightDescription")
    public JAXBElement<String> createAccessRightDescription(String value) {
        return new JAXBElement<String>(_AccessRightDescription_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Title")
    public JAXBElement<String> createTitle(String value) {
        return new JAXBElement<String>(_Title_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CoverageType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Coverage")
    public JAXBElement<CoverageType> createCoverage(CoverageType value) {
        return new JAXBElement<CoverageType>(_Coverage_QNAME, CoverageType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "IsReferencedBy")
    public JAXBElement<String> createIsReferencedBy(String value) {
        return new JAXBElement<String>(_IsReferencedBy_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OrganisationType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Organisation")
    public JAXBElement<OrganisationType> createOrganisation(OrganisationType value) {
        return new JAXBElement<OrganisationType>(_Organisation_QNAME, OrganisationType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Checker")
    public JAXBElement<String> createChecker(String value) {
        return new JAXBElement<String>(_Checker_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "TransferContractId")
    public JAXBElement<String> createTransferContractId(String value) {
        return new JAXBElement<String>(_TransferContractId_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "RetentionPeriodEnd")
    public JAXBElement<XMLGregorianCalendar> createRetentionPeriodEnd(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_RetentionPeriodEnd_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Available")
    public JAXBElement<XMLGregorianCalendar> createAvailable(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_Available_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "HasVersion")
    public JAXBElement<String> createHasVersion(String value) {
        return new JAXBElement<String>(_HasVersion_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MainFunctionType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "MainFunction")
    public JAXBElement<MainFunctionType> createMainFunction(MainFunctionType value) {
        return new JAXBElement<MainFunctionType>(_MainFunction_QNAME, MainFunctionType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Rights")
    public JAXBElement<String> createRights(String value) {
        return new JAXBElement<String>(_Rights_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DocumentType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Document")
    public JAXBElement<DocumentType> createDocument(DocumentType value) {
        return new JAXBElement<DocumentType>(_Document_QNAME, DocumentType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ContactPersonType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "ContactPerson")
    public JAXBElement<ContactPersonType> createContactPerson(ContactPersonType value) {
        return new JAXBElement<ContactPersonType>(_ContactPerson_QNAME, ContactPersonType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SubFunctionType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "SubFunction")
    public JAXBElement<SubFunctionType> createSubFunction(SubFunctionType value) {
        return new JAXBElement<SubFunctionType>(_SubFunction_QNAME, SubFunctionType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Encryption")
    public JAXBElement<String> createEncryption(String value) {
        return new JAXBElement<String>(_Encryption_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Address")
    public JAXBElement<String> createAddress(String value) {
        return new JAXBElement<String>(_Address_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Owner")
    public JAXBElement<String> createOwner(String value) {
        return new JAXBElement<String>(_Owner_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PersonType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Person")
    public JAXBElement<PersonType> createPerson(PersonType value) {
        return new JAXBElement<PersonType>(_Person_QNAME, PersonType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TimePeriodType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Valid")
    public JAXBElement<TimePeriodType> createValid(TimePeriodType value) {
        return new JAXBElement<TimePeriodType>(_Valid_QNAME, TimePeriodType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Jurisdiction")
    public JAXBElement<String> createJurisdiction(String value) {
        return new JAXBElement<String>(_Jurisdiction_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Accepted")
    public JAXBElement<XMLGregorianCalendar> createAccepted(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_Accepted_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "FunctionCode")
    public JAXBElement<String> createFunctionCode(String value) {
        return new JAXBElement<String>(_FunctionCode_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Audience")
    public JAXBElement<String> createAudience(String value) {
        return new JAXBElement<String>(_Audience_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "IsReplacedBy")
    public JAXBElement<String> createIsReplacedBy(String value) {
        return new JAXBElement<String>(_IsReplacedBy_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "UseType")
    public JAXBElement<String> createUseType(String value) {
        return new JAXBElement<String>(_UseType_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Status")
    public JAXBElement<String> createStatus(String value) {
        return new JAXBElement<String>(_Status_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AgentType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Agent")
    public JAXBElement<AgentType> createAgent(AgentType value) {
        return new JAXBElement<AgentType>(_Agent_QNAME, AgentType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Finished")
    public JAXBElement<XMLGregorianCalendar> createFinished(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_Finished_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Temporal")
    public JAXBElement<String> createTemporal(String value) {
        return new JAXBElement<String>(_Temporal_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "IsFormatOf")
    public JAXBElement<String> createIsFormatOf(String value) {
        return new JAXBElement<String>(_IsFormatOf_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "HasPart")
    public JAXBElement<String> createHasPart(String value) {
        return new JAXBElement<String>(_HasPart_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Created")
    public JAXBElement<XMLGregorianCalendar> createCreated(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_Created_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TransferInformationType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "TransferInformation")
    public JAXBElement<TransferInformationType> createTransferInformation(TransferInformationType value) {
        return new JAXBElement<TransferInformationType>(_TransferInformation_QNAME, TransferInformationType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "SecurityPeriodEnd")
    public JAXBElement<XMLGregorianCalendar> createSecurityPeriodEnd(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_SecurityPeriodEnd_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "MediumID")
    public JAXBElement<String> createMediumID(String value) {
        return new JAXBElement<String>(_MediumID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Description")
    public JAXBElement<String> createDescription(String value) {
        return new JAXBElement<String>(_Description_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "HashAlgorithm")
    public JAXBElement<String> createHashAlgorithm(String value) {
        return new JAXBElement<String>(_HashAlgorithm_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "PhoneNumber")
    public JAXBElement<String> createPhoneNumber(String value) {
        return new JAXBElement<String>(_PhoneNumber_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "NativeId")
    public JAXBElement<String> createNativeId(String value) {
        return new JAXBElement<String>(_NativeId_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "NotificationPeriod")
    public JAXBElement<String> createNotificationPeriod(String value) {
        return new JAXBElement<String>(_NotificationPeriod_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "ArrivalDate")
    public JAXBElement<XMLGregorianCalendar> createArrivalDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_ArrivalDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AuthenticityType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Authenticity")
    public JAXBElement<AuthenticityType> createAuthenticity(AuthenticityType value) {
        return new JAXBElement<AuthenticityType>(_Authenticity_QNAME, AuthenticityType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Gathered")
    public JAXBElement<XMLGregorianCalendar> createGathered(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_Gathered_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Type")
    public JAXBElement<String> createType(String value) {
        return new JAXBElement<String>(_Type_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PersonalDataType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "PersonalData")
    public JAXBElement<PersonalDataType> createPersonalData(PersonalDataType value) {
        return new JAXBElement<PersonalDataType>(_PersonalData_QNAME, PersonalDataType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "AlternativeTitle")
    public JAXBElement<String> createAlternativeTitle(String value) {
        return new JAXBElement<String>(_AlternativeTitle_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "IsVersionOf")
    public JAXBElement<String> createIsVersionOf(String value) {
        return new JAXBElement<String>(_IsVersionOf_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "CorporateName")
    public JAXBElement<String> createCorporateName(String value) {
        return new JAXBElement<String>(_CorporateName_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Replaces")
    public JAXBElement<String> createReplaces(String value) {
        return new JAXBElement<String>(_Replaces_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "IsRequiredBy")
    public JAXBElement<String> createIsRequiredBy(String value) {
        return new JAXBElement<String>(_IsRequiredBy_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Delivered")
    public JAXBElement<XMLGregorianCalendar> createDelivered(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_Delivered_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Abstract")
    public JAXBElement<String> createAbstract(String value) {
        return new JAXBElement<String>(_Abstract_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "MetadataSchema")
    public JAXBElement<String> createMetadataSchema(String value) {
        return new JAXBElement<String>(_MetadataSchema_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "Path")
    public JAXBElement<String> createPath(String value) {
        return new JAXBElement<String>(_Path_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1", name = "SignatureDescription")
    public JAXBElement<String> createSignatureDescription(String value) {
        return new JAXBElement<String>(_SignatureDescription_QNAME, String.class, null, value);
    }

}
