
package lupapalvelu.tiedonohjaus;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Subject" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Created" maxOccurs="unbounded"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Issued" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Sent" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Modified" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Available" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Acquired" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}NativeId"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}OtherId" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Language" maxOccurs="unbounded"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Description" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Abstract" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Restriction"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Title"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}CaseFileRelation" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}RetentionPeriod"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}RetentionReason"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}RetentionPeriodEnd" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Status"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}ClassificationScheme"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Function"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Agent" maxOccurs="unbounded"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Type" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Finished" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}ElectronicNotification" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Action" maxOccurs="unbounded"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Custom" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
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
    "caseFileRelation",
    "retentionPeriod",
    "retentionReason",
    "retentionPeriodEnd",
    "status",
    "classificationScheme",
    "function",
    "agent",
    "type",
    "finished",
    "electronicNotification",
    "action",
    "custom"
})
@XmlRootElement(name = "CaseFile")
public class CaseFile {

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
    @XmlElement(name = "CaseFileRelation")
    protected List<CaseFileRelation> caseFileRelation;
    @XmlElement(name = "RetentionPeriod", required = true)
    protected BigInteger retentionPeriod;
    @XmlElement(name = "RetentionReason", required = true)
    protected String retentionReason;
    @XmlElement(name = "RetentionPeriodEnd")
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar retentionPeriodEnd;
    @XmlElement(name = "Status", required = true)
    protected String status;
    @XmlElement(name = "ClassificationScheme", required = true)
    protected ClassificationScheme classificationScheme;
    @XmlElement(name = "Function", required = true)
    protected String function;
    @XmlElement(name = "Agent", required = true)
    protected List<AgentType> agent;
    @XmlElement(name = "Type")
    protected String type;
    @XmlElement(name = "Finished")
    @XmlSchemaType(name = "date")
    protected List<XMLGregorianCalendar> finished;
    @XmlElement(name = "ElectronicNotification")
    protected ElectronicNotificationType electronicNotification;
    @XmlElement(name = "Action", required = true)
    protected List<ActionType> action;
    @XmlElement(name = "Custom")
    protected Custom custom;

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
     * Gets the value of the caseFileRelation property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the caseFileRelation property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCaseFileRelation().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link CaseFileRelation }
     * 
     * 
     */
    public List<CaseFileRelation> getCaseFileRelation() {
        if (caseFileRelation == null) {
            caseFileRelation = new ArrayList<CaseFileRelation>();
        }
        return this.caseFileRelation;
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
     * Gets the value of the classificationScheme property.
     * 
     * @return
     *     possible object is
     *     {@link ClassificationScheme }
     *     
     */
    public ClassificationScheme getClassificationScheme() {
        return classificationScheme;
    }

    /**
     * Sets the value of the classificationScheme property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClassificationScheme }
     *     
     */
    public void setClassificationScheme(ClassificationScheme value) {
        this.classificationScheme = value;
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
     * Gets the value of the finished property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the finished property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getFinished().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link XMLGregorianCalendar }
     * 
     * 
     */
    public List<XMLGregorianCalendar> getFinished() {
        if (finished == null) {
            finished = new ArrayList<XMLGregorianCalendar>();
        }
        return this.finished;
    }

    /**
     * Gets the value of the electronicNotification property.
     * 
     * @return
     *     possible object is
     *     {@link ElectronicNotificationType }
     *     
     */
    public ElectronicNotificationType getElectronicNotification() {
        return electronicNotification;
    }

    /**
     * Sets the value of the electronicNotification property.
     * 
     * @param value
     *     allowed object is
     *     {@link ElectronicNotificationType }
     *     
     */
    public void setElectronicNotification(ElectronicNotificationType value) {
        this.electronicNotification = value;
    }

    /**
     * Gets the value of the action property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the action property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAction().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ActionType }
     * 
     * 
     */
    public List<ActionType> getAction() {
        if (action == null) {
            action = new ArrayList<ActionType>();
        }
        return this.action;
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

}
