/* Used for groups of mutually exclusive toggleable buttons.
 * Provides an alternative to the radio-button component.
 *
 * Parameters [optional]:
 * selectedValue        The observable holding the selected value of the group of radio fields this one belongs to
 * value                The non-observable value that should be used when this field is selected
 *
 * Also works with parameters inherited from InputFieldModel, including label, lLabel, etc.
 */
LUPAPISTE.RadioFieldModel = function(params) {
  "use strict";
  params = params || {};

  // Construct super
  ko.utils.extend(this, new LUPAPISTE.InputFieldModel(params));

  this.selectedValue = params.selectedValue;
};
