
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
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Requires" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}IsRequiredBy" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}HasPart" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}IsPartOf" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}HasFormat" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}IsFormatOf" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}HasVersion" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}IsVersionOf" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}HasRedaction" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}IsRedactionOf" minOccurs="0"/>
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
    "isReferencedBy",
    "requires",
    "isRequiredBy",
    "hasPart",
    "isPartOf",
    "hasFormat",
    "isFormatOf",
    "hasVersion",
    "isVersionOf",
    "hasRedaction",
    "isRedactionOf"
})
@XmlRootElement(name = "RecordRelation")
public class RecordRelation {

    @XmlElement(name = "Replaces")
    protected String replaces;
    @XmlElement(name = "IsReplacedBy")
    protected String isReplacedBy;
    @XmlElement(name = "References")
    protected String references;
    @XmlElement(name = "IsReferencedBy")
    protected String isReferencedBy;
    @XmlElement(name = "Requires")
    protected String requires;
    @XmlElement(name = "IsRequiredBy")
    protected String isRequiredBy;
    @XmlElement(name = "HasPart")
    protected String hasPart;
    @XmlElement(name = "IsPartOf")
    protected String isPartOf;
    @XmlElement(name = "HasFormat")
    protected String hasFormat;
    @XmlElement(name = "IsFormatOf")
    protected String isFormatOf;
    @XmlElement(name = "HasVersion")
    protected String hasVersion;
    @XmlElement(name = "IsVersionOf")
    protected String isVersionOf;
    @XmlElement(name = "HasRedaction")
    protected String hasRedaction;
    @XmlElement(name = "IsRedactionOf")
    protected String isRedactionOf;

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

    /**
     * Gets the value of the requires property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRequires() {
        return requires;
    }

    /**
     * Sets the value of the requires property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRequires(String value) {
        this.requires = value;
    }

    /**
     * Gets the value of the isRequiredBy property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getIsRequiredBy() {
        return isRequiredBy;
    }

    /**
     * Sets the value of the isRequiredBy property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setIsRequiredBy(String value) {
        this.isRequiredBy = value;
    }

    /**
     * Gets the value of the hasPart property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHasPart() {
        return hasPart;
    }

    /**
     * Sets the value of the hasPart property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHasPart(String value) {
        this.hasPart = value;
    }

    /**
     * Gets the value of the isPartOf property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getIsPartOf() {
        return isPartOf;
    }

    /**
     * Sets the value of the isPartOf property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setIsPartOf(String value) {
        this.isPartOf = value;
    }

    /**
     * Gets the value of the hasFormat property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHasFormat() {
        return hasFormat;
    }

    /**
     * Sets the value of the hasFormat property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHasFormat(String value) {
        this.hasFormat = value;
    }

    /**
     * Gets the value of the isFormatOf property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getIsFormatOf() {
        return isFormatOf;
    }

    /**
     * Sets the value of the isFormatOf property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setIsFormatOf(String value) {
        this.isFormatOf = value;
    }

    /**
     * Gets the value of the hasVersion property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHasVersion() {
        return hasVersion;
    }

    /**
     * Sets the value of the hasVersion property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHasVersion(String value) {
        this.hasVersion = value;
    }

    /**
     * Gets the value of the isVersionOf property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getIsVersionOf() {
        return isVersionOf;
    }

    /**
     * Sets the value of the isVersionOf property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setIsVersionOf(String value) {
        this.isVersionOf = value;
    }

    /**
     * Gets the value of the hasRedaction property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHasRedaction() {
        return hasRedaction;
    }

    /**
     * Sets the value of the hasRedaction property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHasRedaction(String value) {
        this.hasRedaction = value;
    }

    /**
     * Gets the value of the isRedactionOf property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getIsRedactionOf() {
        return isRedactionOf;
    }

    /**
     * Sets the value of the isRedactionOf property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setIsRedactionOf(String value) {
        this.isRedactionOf = value;
    }

}
