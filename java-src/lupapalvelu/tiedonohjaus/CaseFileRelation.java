
package lupapalvelu.tiedonohjaus;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


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
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Replaces" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}IsReplacedBy" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}References" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}IsReferencedBy" minOccurs="0"/>
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
    "replaces",
    "isReplacedBy",
    "references",
    "isReferencedBy"
})
@XmlRootElement(name = "CaseFileRelation")
public class CaseFileRelation {

    @XmlElement(name = "Replaces")
    protected String replaces;
    @XmlElement(name = "IsReplacedBy")
    protected String isReplacedBy;
    @XmlElement(name = "References")
    protected String references;
    @XmlElement(name = "IsReferencedBy")
    protected String isReferencedBy;

    /**
     * Gets the value of the replaces property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getReplaces() {
        return replaces;
    }

    /**
     * Sets the value of the replaces property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setReplaces(String value) {
        this.replaces = value;
    }

    /**
     * Gets the value of the isReplacedBy property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getIsReplacedBy() {
        return isReplacedBy;
    }

    /**
     * Sets the value of the isReplacedBy property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setIsReplacedBy(String value) {
        this.isReplacedBy = value;
    }

    /**
     * Gets the value of the references property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getReferences() {
        return references;
    }

    /**
     * Sets the value of the references property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setReferences(String value) {
        this.references = value;
    }

    /**
     * Gets the value of the isReferencedBy property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getIsReferencedBy() {
        return isReferencedBy;
    }

    /**
     * Sets the value of the isReferencedBy property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setIsReferencedBy(String value) {
        this.isReferencedBy = value;
    }

}
