
package lupapalvelu.tiedonohjaus;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;


/**
 * <p>Java class for RecordType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="RecordType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Subject" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Created" maxOccurs="unbounded"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Issued" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Sent" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Modified" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Available" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Acquired" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}NativeId"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}OtherId" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Language" maxOccurs="unbounded"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Description" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Abstract" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Restriction"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Title"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}StorageLocation" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}RecordRelation" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}RetentionPeriod"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}RetentionReason"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}RetentionPeriodEnd" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Status"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Function"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Type"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Accepted" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Gathered" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Valid" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Authenticity"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}SignatureDescription" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Coverage" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Audience" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Source" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Rights" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}ProtectionClass" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}AlternativeTitle" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Version" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Document" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Custom" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Agent" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "RecordType", propOrder = {
    "subject",
    "created",
    "issued",
    "sent",
    "modified",
    "available",
    "acquired",
    "nativeId",
    "otherId",
    "language",
    "description",
    "_abstract",
    "restriction",
    "title",
    "storageLocation",
    "recordRelation",
    "retentionPeriod",
    "retentionReason",
    "retentionPeriodEnd",
    "status",
    "function",
    "type",
    "accepted",
    "gathered",
    "valid",
    "authenticity",
    "signatureDescription",
    "coverage",
    "audience",
    "source",
    "rights",
    "protectionClass",
    "alternativeTitle",
    "version",
    "document",
    "custom",
    "agent"
})
public class RecordType {

    @XmlElement(name = "Subject")
    protected List<SubjectType> subject;
    @XmlElement(name = "Created", required = true)
    @XmlSchemaType(name = "date")
    protected List<XMLGregorianCalendar> created;
    @XmlElement(name = "Issued")
    @XmlSchemaType(name = "date")
    protected List<XMLGregorianCalendar> issued;
    @XmlElement(name = "Sent")
    @XmlSchemaType(name = "date")
    protected List<XMLGregorianCalendar> sent;
    @XmlElement(name = "Modified")
    @XmlSchemaType(name = "date")
    protected List<XMLGregorianCalendar> modified;
    @XmlElement(name = "Available")
    @XmlSchemaType(name = "date")
    protected List<XMLGregorianCalendar> available;
    @XmlElement(name = "Acquired")
    @XmlSchemaType(name = "date")
    protected List<XMLGregorianCalendar> acquired;
    @XmlElement(name = "NativeId", required = true)
    protected String nativeId;
    @XmlElement(name = "OtherId")
    protected List<String> otherId;
    @XmlElement(name = "Language", required = true)
    protected List<String> language;
    @XmlElement(name = "Description")
    protected List<String> description;
    @XmlElement(name = "Abstract")
    protected List<String> _abstract;
    @XmlElement(name = "Restriction", required = true)
    protected RestrictionType restriction;
    @XmlElement(name = "Title", required = true)
    protected String title;
    @XmlElement(name = "StorageLocation")
    protected String storageLocation;
    @XmlElement(name = "RecordRelation")
    protected List<RecordRelation> recordRelation;
    @XmlElement(name = "RetentionPeriod", required = true)
    protected BigInteger retentionPeriod;
    @XmlElement(name = "RetentionReason", required = true)
    protected String retentionReason;
    @XmlElement(name = "RetentionPeriodEnd")
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar retentionPeriodEnd;
    @XmlElement(name = "Status", required = true)
    protected String status;
    @XmlElement(name = "Function", required = true)
    protected String function;
    @XmlElement(name = "Type", required = true)
    protected String type;
    @XmlElement(name = "Accepted")
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar accepted;
    @XmlElement(name = "Gathered")
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar gathered;
    @XmlElement(name = "Valid")
    protected TimePeriodType valid;
    @XmlElement(name = "Authenticity", required = true)
    protected AuthenticityType authenticity;
    @XmlElement(name = "SignatureDescription")
    protected String signatureDescription;
    @XmlElement(name = "Coverage")
    protected CoverageType coverage;
    @XmlElement(name = "Audience")
    protected List<String> audience;
    @XmlElement(name = "Source")
    protected List<String> source;
    @XmlElement(name = "Rights")
    protected List<String> rights;
    @XmlElement(name = "ProtectionClass")
    protected List<String> protectionClass;
    @XmlElement(name = "AlternativeTitle")
    protected List<String> alternativeTitle;
    @XmlElement(name = "Version")
    protected String version;
    @XmlElement(name = "Document")
    protected List<DocumentType> document;
    @XmlElement(name = "Custom")
    protected Custom custom;
    @XmlElement(name = "Agent")
    protected List<AgentType> agent;

    /**
     * Gets the value of the subject property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the subject property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSubject().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link SubjectType }
     * 
     * 
     */
    public List<SubjectType> getSubject() {
        if (subject == null) {
            subject = new ArrayList<SubjectType>();
        }
        return this.subject;
    }

    /**
     * Gets the value of the created property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the created property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCreated().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link XMLGregorianCalendar }
     * 
     * 
     */
    public List<XMLGregorianCalendar> getCreated() {
        if (created == null) {
            created = new ArrayList<XMLGregorianCalendar>();
        }
        return this.created;
    }

    /**
     * Gets the value of the issued property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the issued property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getIssued().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link XMLGregorianCalendar }
     * 
     * 
     */
    public List<XMLGregorianCalendar> getIssued() {
        if (issued == null) {
            issued = new ArrayList<XMLGregorianCalendar>();
        }
        return this.issued;
    }

    /**
     * Gets the value of the sent property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the sent property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSent().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link XMLGregorianCalendar }
     * 
     * 
     */
    public List<XMLGregorianCalendar> getSent() {
        if (sent == null) {
            sent = new ArrayList<XMLGregorianCalendar>();
        }
        return this.sent;
    }

    /**
     * Gets the value of the modified property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the modified property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getModified().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link XMLGregorianCalendar }
     * 
     * 
     */
    public List<XMLGregorianCalendar> getModified() {
        if (modified == null) {
            modified = new ArrayList<XMLGregorianCalendar>();
        }
        return this.modified;
    }

    /**
     * Gets the value of the available property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the available property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAvailable().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link XMLGregorianCalendar }
     * 
     * 
     */
    public List<XMLGregorianCalendar> getAvailable() {
        if (available == null) {
            available = new ArrayList<XMLGregorianCalendar>();
        }
        return this.available;
    }

    /**
     * Gets the value of the acquired property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the acquired property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAcquired().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link XMLGregorianCalendar }
     * 
     * 
     */
    public List<XMLGregorianCalendar> getAcquired() {
        if (acquired == null) {
            acquired = new ArrayList<XMLGregorianCalendar>();
        }
        return this.acquired;
    }

    /**
     * Gets the value of the nativeId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getNativeId() {
        return nativeId;
    }

    /**
     * Sets the value of the nativeId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setNativeId(String value) {
        this.nativeId = value;
    }

    /**
     * Gets the value of the otherId property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the otherId property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getOtherId().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getOtherId() {
        if (otherId == null) {
            otherId = new ArrayList<String>();
        }
        return this.otherId;
    }

    /**
     * Gets the value of the language property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the language property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getLanguage().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getLanguage() {
        if (language == null) {
            language = new ArrayList<String>();
        }
        return this.language;
    }

    /**
     * Gets the value of the description property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the description property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDescription().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getDescription() {
        if (description == null) {
            description = new ArrayList<String>();
        }
        return this.description;
    }

    /**
     * Gets the value of the abstract property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the abstract property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAbstract().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getAbstract() {
        if (_abstract == null) {
            _abstract = new ArrayList<String>();
        }
        return this._abstract;
    }

    /**
     * Gets the value of the restriction property.
     * 
     * @return
     *     possible object is
     *     {@link RestrictionType }
     *     
     */
    public RestrictionType getRestriction() {
        return restriction;
    }

    /**
     * Sets the value of the restriction property.
     * 
     * @param value
     *     allowed object is
     *     {@link RestrictionType }
     *     
     */
    public void setRestriction(RestrictionType value) {
        this.restriction = value;
    }

    /**
     * Gets the value of the title property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the value of the title property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTitle(String value) {
        this.title = value;
    }

    /**
     * Gets the value of the storageLocation property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getStorageLocation() {
        return storageLocation;
    }

    /**
     * Sets the value of the storageLocation property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setStorageLocation(String value) {
        this.storageLocation = value;
    }

    /**
     * Gets the value of the recordRelation property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the recordRelation property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getRecordRelation().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link RecordRelation }
     * 
     * 
     */
    public List<RecordRelation> getRecordRelation() {
        if (recordRelation == null) {
            recordRelation = new ArrayList<RecordRelation>();
        }
        return this.recordRelation;
    }

    /**
     * Gets the value of the retentionPeriod property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getRetentionPeriod() {
        return retentionPeriod;
    }

    /**
     * Sets the value of the retentionPeriod property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setRetentionPeriod(BigInteger value) {
        this.retentionPeriod = value;
    }

    /**
     * Gets the value of the retentionReason property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRetentionReason() {
        return retentionReason;
    }

    /**
     * Sets the value of the retentionReason property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRetentionReason(String value) {
        this.retentionReason = value;
    }

    /**
     * Gets the value of the retentionPeriodEnd property.
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getRetentionPeriodEnd() {
        return retentionPeriodEnd;
    }

    /**
     * Sets the value of the retentionPeriodEnd property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setRetentionPeriodEnd(XMLGregorianCalendar value) {
        this.retentionPeriodEnd = value;
    }

    /**
     * Gets the value of the status property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the value of the status property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setStatus(String value) {
        this.status = value;
    }

    /**
     * Gets the value of the function property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFunction() {
        return function;
    }

    /**
     * Sets the value of the function property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFunction(String value) {
        this.function = value;
    }

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setType(String value) {
        this.type = value;
    }

    /**
     * Gets the value of the accepted property.
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getAccepted() {
        return accepted;
    }

    /**
     * Sets the value of the accepted property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setAccepted(XMLGregorianCalendar value) {
        this.accepted = value;
    }

    /**
     * Gets the value of the gathered property.
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getGathered() {
        return gathered;
    }

    /**
     * Sets the value of the gathered property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setGathered(XMLGregorianCalendar value) {
        this.gathered = value;
    }

    /**
     * Gets the value of the valid property.
     * 
     * @return
     *     possible object is
     *     {@link TimePeriodType }
     *     
     */
    public TimePeriodType getValid() {
        return valid;
    }

    /**
     * Sets the value of the valid property.
     * 
     * @param value
     *     allowed object is
     *     {@link TimePeriodType }
     *     
     */
    public void setValid(TimePeriodType value) {
        this.valid = value;
    }

    /**
     * Gets the value of the authenticity property.
     * 
     * @return
     *     possible object is
     *     {@link AuthenticityType }
     *     
     */
    public AuthenticityType getAuthenticity() {
        return authenticity;
    }

    /**
     * Sets the value of the authenticity property.
     * 
     * @param value
     *     allowed object is
     *     {@link AuthenticityType }
     *     
     */
    public void setAuthenticity(AuthenticityType value) {
        this.authenticity = value;
    }

    /**
     * Gets the value of the signatureDescription property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSignatureDescription() {
        return signatureDescription;
    }

    /**
     * Sets the value of the signatureDescription property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSignatureDescription(String value) {
        this.signatureDescription = value;
    }

    /**
     * Gets the value of the coverage property.
     * 
     * @return
     *     possible object is
     *     {@link CoverageType }
     *     
     */
    public CoverageType getCoverage() {
        return coverage;
    }

    /**
     * Sets the value of the coverage property.
     * 
     * @param value
     *     allowed object is
     *     {@link CoverageType }
     *     
     */
    public void setCoverage(CoverageType value) {
        this.coverage = value;
    }

    /**
     * Gets the value of the audience property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the audience property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAudience().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getAudience() {
        if (audience == null) {
            audience = new ArrayList<String>();
        }
        return this.audience;
    }

    /**
     * Gets the value of the source property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the source property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSource().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getSource() {
        if (source == null) {
            source = new ArrayList<String>();
        }
        return this.source;
    }

    /**
     * Gets the value of the rights property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the rights property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getRights().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getRights() {
        if (rights == null) {
            rights = new ArrayList<String>();
        }
        return this.rights;
    }

    /**
     * Gets the value of the protectionClass property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the protectionClass property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getProtectionClass().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getProtectionClass() {
        if (protectionClass == null) {
            protectionClass = new ArrayList<String>();
        }
        return this.protectionClass;
    }

    /**
     * Gets the value of the alternativeTitle property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the alternativeTitle property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAlternativeTitle().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getAlternativeTitle() {
        if (alternativeTitle == null) {
            alternativeTitle = new ArrayList<String>();
        }
        return this.alternativeTitle;
    }

    /**
     * Gets the value of the version property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the value of the version property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVersion(String value) {
        this.version = value;
    }

    /**
     * Gets the value of the document property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the document property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDocument().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link DocumentType }
     * 
     * 
     */
    public List<DocumentType> getDocument() {
        if (document == null) {
            document = new ArrayList<DocumentType>();
        }
        return this.document;
    }

    /**
     * Gets the value of the custom property.
     * 
     * @return
     *     possible object is
     *     {@link Custom }
     *     
     */
    public Custom getCustom() {
        return custom;
    }

    /**
     * Sets the value of the custom property.
     * 
     * @param value
     *     allowed object is
     *     {@link Custom }
     *     
     */
    public void setCustom(Custom value) {
        this.custom = value;
    }

    /**
     * Gets the value of the agent property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the agent property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAgent().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link AgentType }
     * 
     * 
     */
    public List<AgentType> getAgent() {
        if (agent == null) {
            agent = new ArrayList<AgentType>();
        }
        return this.agent;
    }

}
