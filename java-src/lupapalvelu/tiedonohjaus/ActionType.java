
package lupapalvelu.tiedonohjaus;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;


/**
 * <p>Java class for ActionType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ActionType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Accepted" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Issued" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Created"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Sent" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Modified" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Valid" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Description" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Abstract" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Title"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Custom" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Agent" maxOccurs="unbounded"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Type"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Record" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ActionType", propOrder = {
    "accepted",
    "issued",
    "created",
    "sent",
    "modified",
    "valid",
    "description",
    "_abstract",
    "title",
    "custom",
    "agent",
    "type",
    "record"
})
public class ActionType {

    @XmlElement(name = "Accepted")
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar accepted;
    @XmlElement(name = "Issued")
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar issued;
    @XmlElement(name = "Created", required = true)
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar created;
    @XmlElement(name = "Sent")
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar sent;
    @XmlElement(name = "Modified")
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar modified;
    @XmlElement(name = "Valid")
    protected TimePeriodType valid;
    @XmlElement(name = "Description")
    protected List<String> description;
    @XmlElement(name = "Abstract")
    protected List<String> _abstract;
    @XmlElement(name = "Title", required = true)
    protected String title;
    @XmlElement(name = "Custom")
    protected Custom custom;
    @XmlElement(name = "Agent", required = true)
    protected List<AgentType> agent;
    @XmlElement(name = "Type", required = true)
    protected String type;
    @XmlElement(name = "Record")
    protected List<RecordType> record;

    /**
     * SÄHKE2 Metatietomalli: 5.1.1 Hyväksytty (Date.Accepted)
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
     * SÄHKE2 Metatietomalli: 5.1.2 Julkistettu (Date.Issued)
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getIssued() {
        return issued;
    }

    /**
     * Sets the value of the issued property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setIssued(XMLGregorianCalendar value) {
        this.issued = value;
    }

    /**
     * SÄHKE2 Metatietomalli: 5.1.3 Laadittu (Date.Created)
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getCreated() {
        return created;
    }

    /**
     * Sets the value of the created property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setCreated(XMLGregorianCalendar value) {
        this.created = value;
    }

    /**
     * SÄHKE2 Metatietomalli: 5.1.4 Lähetetty (Date.Sent)
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getSent() {
        return sent;
    }

    /**
     * Sets the value of the sent property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setSent(XMLGregorianCalendar value) {
        this.sent = value;
    }

    /**
     * SÄHKE2 Metatietomalli: 5.1.5 Muokattu (Date.Modified)
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getModified() {
        return modified;
    }

    /**
     * Sets the value of the modified property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setModified(XMLGregorianCalendar value) {
        this.modified = value;
    }

    /**
     * SÄHKE2 Metatietomalli: 5.1.6 Voimassaoloaika (Date.Valid)
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
     * SÄHKE2 Metatietomalli: 5.2 Kuvaus Gets the value of the description property.
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
     * SÄHKE2 Metatietomalli: 5.3 Nimeke
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
     * SÄHKE2 Metatietomalli: 5.4 Organisaatiokohtaiset metatiedot
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
     * SÄHKE2 Metatietomalli: 5.5 Toimija Gets the value of the agent property.
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
     * SÄHKE2 Metatietomalli: 5.6 Tyyppi (Type)
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
     * Gets the value of the record property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the record property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getRecord().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link RecordType }
     * 
     * 
     */
    public List<RecordType> getRecord() {
        if (record == null) {
            record = new ArrayList<RecordType>();
        }
        return this.record;
    }

}
