
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
 * <p>Java class for RestrictionType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="RestrictionType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}PublicityClass"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}SecurityPeriod" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}SecurityPeriodEnd" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}SecurityReason" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}ProtectionLevel" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}SecurityClass" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}PersonalData"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Person" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Owner" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}AccessRight" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "RestrictionType", propOrder = {
    "publicityClass",
    "securityPeriod",
    "securityPeriodEnd",
    "securityReason",
    "protectionLevel",
    "securityClass",
    "personalData",
    "person",
    "owner",
    "accessRight"
})
public class RestrictionType {

    @XmlElement(name = "PublicityClass", required = true)
    @XmlSchemaType(name = "string")
    protected PublicityClassType publicityClass;
    @XmlElement(name = "SecurityPeriod")
    protected BigInteger securityPeriod;
    @XmlElement(name = "SecurityPeriodEnd")
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar securityPeriodEnd;
    @XmlElement(name = "SecurityReason")
    protected String securityReason;
    @XmlElement(name = "ProtectionLevel")
    @XmlSchemaType(name = "string")
    protected ProtectionLevelType protectionLevel;
    @XmlElement(name = "SecurityClass")
    @XmlSchemaType(name = "string")
    protected SecurityClassType securityClass;
    @XmlElement(name = "PersonalData", required = true)
    @XmlSchemaType(name = "string")
    protected PersonalDataType personalData;
    @XmlElement(name = "Person")
    protected List<PersonType> person;
    @XmlElement(name = "Owner")
    protected List<String> owner;
    @XmlElement(name = "AccessRight")
    protected List<AccessRightType> accessRight;

    /**
     * Gets the value of the publicityClass property.
     * 
     * @return
     *     possible object is
     *     {@link PublicityClassType }
     *     
     */
    public PublicityClassType getPublicityClass() {
        return publicityClass;
    }

    /**
     * Sets the value of the publicityClass property.
     * 
     * @param value
     *     allowed object is
     *     {@link PublicityClassType }
     *     
     */
    public void setPublicityClass(PublicityClassType value) {
        this.publicityClass = value;
    }

    /**
     * Gets the value of the securityPeriod property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getSecurityPeriod() {
        return securityPeriod;
    }

    /**
     * Sets the value of the securityPeriod property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setSecurityPeriod(BigInteger value) {
        this.securityPeriod = value;
    }

    /**
     * Gets the value of the securityPeriodEnd property.
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getSecurityPeriodEnd() {
        return securityPeriodEnd;
    }

    /**
     * Sets the value of the securityPeriodEnd property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setSecurityPeriodEnd(XMLGregorianCalendar value) {
        this.securityPeriodEnd = value;
    }

    /**
     * Gets the value of the securityReason property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSecurityReason() {
        return securityReason;
    }

    /**
     * Sets the value of the securityReason property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSecurityReason(String value) {
        this.securityReason = value;
    }

    /**
     * Gets the value of the protectionLevel property.
     * 
     * @return
     *     possible object is
     *     {@link ProtectionLevelType }
     *     
     */
    public ProtectionLevelType getProtectionLevel() {
        return protectionLevel;
    }

    /**
     * Sets the value of the protectionLevel property.
     * 
     * @param value
     *     allowed object is
     *     {@link ProtectionLevelType }
     *     
     */
    public void setProtectionLevel(ProtectionLevelType value) {
        this.protectionLevel = value;
    }

    /**
     * Gets the value of the securityClass property.
     * 
     * @return
     *     possible object is
     *     {@link SecurityClassType }
     *     
     */
    public SecurityClassType getSecurityClass() {
        return securityClass;
    }

    /**
     * Sets the value of the securityClass property.
     * 
     * @param value
     *     allowed object is
     *     {@link SecurityClassType }
     *     
     */
    public void setSecurityClass(SecurityClassType value) {
        this.securityClass = value;
    }

    /**
     * Gets the value of the personalData property.
     * 
     * @return
     *     possible object is
     *     {@link PersonalDataType }
     *     
     */
    public PersonalDataType getPersonalData() {
        return personalData;
    }

    /**
     * Sets the value of the personalData property.
     * 
     * @param value
     *     allowed object is
     *     {@link PersonalDataType }
     *     
     */
    public void setPersonalData(PersonalDataType value) {
        this.personalData = value;
    }

    /**
     * Gets the value of the person property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the person property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPerson().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PersonType }
     * 
     * 
     */
    public List<PersonType> getPerson() {
        if (person == null) {
            person = new ArrayList<PersonType>();
        }
        return this.person;
    }

    /**
     * Gets the value of the owner property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the owner property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getOwner().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getOwner() {
        if (owner == null) {
            owner = new ArrayList<String>();
        }
        return this.owner;
    }

    /**
     * Gets the value of the accessRight property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the accessRight property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAccessRight().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link AccessRightType }
     * 
     * 
     */
    public List<AccessRightType> getAccessRight() {
        if (accessRight == null) {
            accessRight = new ArrayList<AccessRightType>();
        }
        return this.accessRight;
    }

}
