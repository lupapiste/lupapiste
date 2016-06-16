
package lupapalvelu.tiedonohjaus;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for TransferInformationType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="TransferInformationType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}NativeId"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Title"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}TransferContractId"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}MetadataSchema"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TransferInformationType", propOrder = {
    "nativeId",
    "title",
    "transferContractId",
    "metadataSchema"
})
public class TransferInformationType {

    @XmlElement(name = "NativeId", required = true)
    protected String nativeId;
    @XmlElement(name = "Title", required = true)
    protected String title;
    @XmlElement(name = "TransferContractId", required = true)
    protected String transferContractId;
    @XmlElement(name = "MetadataSchema", required = true)
    @XmlSchemaType(name = "anyURI")
    protected String metadataSchema;

    /**
     * SÄHKE2 Metatietomalli: 6.1.1 Identifiointitunnus (Identifier.NativeId)
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
     * SÄHKE2 Metatietomalli: 6.1.3 Nimeke (Title)
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
     * Gets the value of the transferContractId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTransferContractId() {
        return transferContractId;
    }

    /**
     * Sets the value of the transferContractId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTransferContractId(String value) {
        this.transferContractId = value;
    }

    /**
     * Gets the value of the metadataSchema property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMetadataSchema() {
        return metadataSchema;
    }

    /**
     * Sets the value of the metadataSchema property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMetadataSchema(String value) {
        this.metadataSchema = value;
    }

}
