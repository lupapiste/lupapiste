
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
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}MainFunction"/>
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
    "mainFunction"
})
@XmlRootElement(name = "ClassificationScheme")
public class ClassificationScheme {

    @XmlElement(name = "MainFunction", required = true)
    protected MainFunctionType mainFunction;

    /**
     * Gets the value of the mainFunction property.
     * 
     * @return
     *     possible object is
     *     {@link MainFunctionType }
     *     
     */
    public MainFunctionType getMainFunction() {
        return mainFunction;
    }

    /**
     * Sets the value of the mainFunction property.
     * 
     * @param value
     *     allowed object is
     *     {@link MainFunctionType }
     *     
     */
    public void setMainFunction(MainFunctionType value) {
        this.mainFunction = value;
    }

}
