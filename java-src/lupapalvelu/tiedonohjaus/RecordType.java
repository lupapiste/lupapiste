
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
 * <p>Java class for RecordType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="RecordType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Created" maxOccurs="unbounded"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}NativeId"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Description" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Title"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Type"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Version" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Agent" maxOccurs="unbounded" minOccurs="0"/>
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
    "created",
    "nativeId",
    "description",
    "title",
    "type",
    "version",
    "agent"
})
public class RecordType {

    @XmlElement(name = "Created", required = true)
    @XmlSchemaType(name = "date")
    protected List<XMLGregorianCalendar> created;
    @XmlElement(name = "NativeId", required = true)
    protected String nativeId;
    @XmlElement(name = "Description")
    protected List<String> description;
    @XmlElement(name = "Title", required = true)
    protected String title;
    @XmlElement(name = "Type", required = true)
    protected String type;
    @XmlElement(name = "Version")
    protected String version;
    @XmlElement(name = "Agent")
    protected List<AgentType> agent;

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
