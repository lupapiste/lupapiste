
package lupapalvelu.tiedonohjaus;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SubFunctionType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SubFunctionType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Title"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}FunctionCode"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}SubFunction" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SubFunctionType", propOrder = {
    "title",
    "functionCode",
    "subFunction"
})
public class SubFunctionType {

    @XmlElement(name = "Title", required = true)
    protected String title;
    @XmlElement(name = "FunctionCode", required = true)
    protected String functionCode;
    @XmlElement(name = "SubFunction")
    protected SubFunctionType subFunction;

    /**
     * SÄHKE2 Metatietomalli: 6.3.5 Nimeke (ClassificationScheme.Title)
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
     * Gets the value of the functionCode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFunctionCode() {
        return functionCode;
    }

    /**
     * Sets the value of the functionCode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFunctionCode(String value) {
        this.functionCode = value;
    }

    /**
     * Gets the value of the subFunction property.
     * 
     * @return
     *     possible object is
     *     {@link SubFunctionType }
     *     
     */
    public SubFunctionType getSubFunction() {
        return subFunction;
    }

    /**
     * Sets the value of the subFunction property.
     * 
     * @param value
     *     allowed object is
     *     {@link SubFunctionType }
     *     
     */
    public void setSubFunction(SubFunctionType value) {
        this.subFunction = value;
    }

}
