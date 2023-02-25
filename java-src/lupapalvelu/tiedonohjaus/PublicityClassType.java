
package lupapalvelu.tiedonohjaus;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PublicityClassType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PublicityClassType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="Julkinen"/>
 *     &lt;enumeration value="Osittain salassapidettävä"/>
 *     &lt;enumeration value="Salassa pidettävä"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "PublicityClassType")
@XmlEnum
public enum PublicityClassType {

    @XmlEnumValue("Julkinen")
    JULKINEN("Julkinen"),
    @XmlEnumValue("Osittain salassapidett\u00e4v\u00e4")
    OSITTAIN_SALASSAPIDETTÄVÄ("Osittain salassapidett\u00e4v\u00e4"),
    @XmlEnumValue("Salassa pidett\u00e4v\u00e4")
    SALASSA_PIDETTÄVÄ("Salassa pidett\u00e4v\u00e4");
    private final String value;

    PublicityClassType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PublicityClassType fromValue(String v) {
        for (PublicityClassType c: PublicityClassType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
