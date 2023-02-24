/* Used for groups of mutually exclusive toggleable buttons.
 * Provides an alternative to the radio-field component.
 *
 * Parameters [optional]:
 * [testId]             The data-test-id for the component
 * selectedValue        The observable holding the selected value of the group of radio buttons this one belongs to
 * value                The non-observable value that should be used when this button is selected
 *
 * Also works with parameters inherited from InputFieldModel, including label, lLabel, etc.
 */
LUPAPISTE.RadioButtonModel = function(params) {
  "use strict";
  params = params || {};
  var self = this;

  // Construct super
  ko.utils.extend(this, new LUPAPISTE.InputFieldModel(params));

  self.testId = params.testId;
  self.selectedValue = params.selectedValue;

  self.clicked = function() {
    self.selectedValue(params.value);
  };

  self.isThisSelected = ko.pureComputed(function() {
    return _.isEqual(params.value, self.selectedValue());
  });

};
