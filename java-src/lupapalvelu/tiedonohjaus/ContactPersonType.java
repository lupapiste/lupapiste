
package lupapalvelu.tiedonohjaus;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ContactPersonType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ContactPersonType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Name"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Address"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}PhoneNumber"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Email"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ContactPersonType", propOrder = {
    "name",
    "address",
    "phoneNumber",
    "email"
})
public class ContactPersonType {

    @XmlElement(name = "Name", required = true)
    protected String name;
    @XmlElement(name = "Address", required = true)
    protected String address;
    @XmlElement(name = "PhoneNumber", required = true)
    protected String phoneNumber;
    @XmlElement(name = "Email", required = true)
    protected String email;

    /**
     * SÄHKE2 Metatietomalli: 6.2.2.1 Nimi (ContactPerson.Name)
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

    /**
     * Gets the value of the address property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAddress() {
        return address;
    }

    /**
     * Sets the value of the address property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAddress(String value) {
        this.address = value;
    }

    /**
     * Gets the value of the phoneNumber property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Sets the value of the phoneNumber property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPhoneNumber(String value) {
        this.phoneNumber = value;
    }

    /**
     * Gets the value of the email property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the value of the email property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setEmail(String value) {
        this.email = value;
    }

}
