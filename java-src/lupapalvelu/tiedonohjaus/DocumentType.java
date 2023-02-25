
package lupapalvelu.tiedonohjaus;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DocumentType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DocumentType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}NativeId"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}UseType"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}File"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Format"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}MediumID" minOccurs="0"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}HashAlgorithm"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}HashValue"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Encryption" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DocumentType", propOrder = {
    "nativeId",
    "useType",
    "file",
    "format",
    "mediumID",
    "hashAlgorithm",
    "hashValue",
    "encryption"
})
public class DocumentType {

    @XmlElement(name = "NativeId", required = true)
    protected String nativeId;
    @XmlElement(name = "UseType", required = true)
    protected String useType;
    @XmlElement(name = "File", required = true)
    protected FileType file;
    @XmlElement(name = "Format", required = true)
    protected FormatType format;
    @XmlElement(name = "MediumID")
    protected String mediumID;
    @XmlElement(name = "HashAlgorithm", required = true)
    protected String hashAlgorithm;
    @XmlElement(name = "HashValue", required = true)
    protected String hashValue;
    @XmlElement(name = "Encryption")
    protected String encryption;

    /**
     * SÄHKE2 Metatietomalli: 6.4.1 Identifiointitunnus (Identifier.NativeId)
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
     * Gets the value of the useType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUseType() {
        return useType;
    }

    /**
     * Sets the value of the useType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUseType(String value) {
        this.useType = value;
    }

    /**
     * Gets the value of the file property.
     * 
     * @return
     *     possible object is
     *     {@link FileType }
     *     
     */
    public FileType getFile() {
        return file;
    }

    /**
     * Sets the value of the file property.
     * 
     * @param value
     *     allowed object is
     *     {@link FileType }
     *     
     */
    public void setFile(FileType value) {
        this.file = value;
    }

    /**
     * Gets the value of the format property.
     * 
     * @return
     *     possible object is
     *     {@link FormatType }
     *     
     */
    public FormatType getFormat() {
        return format;
    }

    /**
     * Sets the value of the format property.
     * 
     * @param value
     *     allowed object is
     *     {@link FormatType }
     *     
     */
    public void setFormat(FormatType value) {
        this.format = value;
    }

    /**
     * Gets the value of the mediumID property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMediumID() {
        return mediumID;
    }

    /**
     * Sets the value of the mediumID property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMediumID(String value) {
        this.mediumID = value;
    }

    /**
     * Gets the value of the hashAlgorithm property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    /**
     * Sets the value of the hashAlgorithm property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHashAlgorithm(String value) {
        this.hashAlgorithm = value;
    }

    /**
     * Gets the value of the hashValue property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHashValue() {
        return hashValue;
    }

    /**
     * Sets the value of the hashValue property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHashValue(String value) {
        this.hashValue = value;
    }

    /**
     * Gets the value of the encryption property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getEncryption() {
        return encryption;
    }

    /**
     * Sets the value of the encryption property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setEncryption(String value) {
        this.encryption = value;
    }

}
