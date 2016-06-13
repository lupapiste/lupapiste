
package lupapalvelu.tiedonohjaus;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AccessRightType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AccessRightType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Name"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Role"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}AccessRightDescription" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AccessRightType", propOrder = {
    "name",
    "role",
    "accessRightDescription"
})
public class AccessRightType {

    @XmlElement(name = "Name", required = true)
    protected String name;
    @XmlElement(name = "Role", required = true)
    protected String role;
    @XmlElement(name = "AccessRightDescription")
    protected String accessRightDescription;

    /**
     * SÄHKE2 Metatietomalli: 2.6.10.1 Henkilö (Restriction.AccessRight.Name)
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
     * Gets the value of the role property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRole() {
        return role;
    }

    /**
     * Sets the value of the role property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRole(String value) {
        this.role = value;
    }

    /**
     * Gets the value of the accessRightDescription property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAccessRightDescription() {
        return accessRightDescription;
    }

    /**
     * Sets the value of the accessRightDescription property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAccessRightDescription(String value) {
        this.accessRightDescription = value;
    }

}
