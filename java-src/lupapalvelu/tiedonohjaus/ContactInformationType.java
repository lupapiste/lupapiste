
package lupapalvelu.tiedonohjaus;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ContactInformationType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ContactInformationType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Organisation"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}ContactPerson"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ContactInformationType", propOrder = {
    "organisation",
    "contactPerson"
})
public class ContactInformationType {

    @XmlElement(name = "Organisation", required = true)
    protected OrganisationType organisation;
    @XmlElement(name = "ContactPerson", required = true)
    protected ContactPersonType contactPerson;

    /**
     * Gets the value of the organisation property.
     * 
     * @return
     *     possible object is
     *     {@link OrganisationType }
     *     
     */
    public OrganisationType getOrganisation() {
        return organisation;
    }

    /**
     * Sets the value of the organisation property.
     * 
     * @param value
     *     allowed object is
     *     {@link OrganisationType }
     *     
     */
    public void setOrganisation(OrganisationType value) {
        this.organisation = value;
    }

    /**
     * Gets the value of the contactPerson property.
     * 
     * @return
     *     possible object is
     *     {@link ContactPersonType }
     *     
     */
    public ContactPersonType getContactPerson() {
        return contactPerson;
    }

    /**
     * Sets the value of the contactPerson property.
     * 
     * @param value
     *     allowed object is
     *     {@link ContactPersonType }
     *     
     */
    public void setContactPerson(ContactPersonType value) {
        this.contactPerson = value;
    }

}
