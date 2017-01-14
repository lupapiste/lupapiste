// Component for various checkbox wrappers.
// The wrappers are defined in _inputs.scss.
// Parameters [optional]:
//  value: Value observable
//  [text]:  Label text.
//  [prefix]: Wrapper prefix (e.g, checkbox, signbox, sectionbox, ... ). (default checkbox)
//  ltext: Label ltext (cannot be observable). Ltext overrides text if both are given.
//  [testId]  Test id prefix for input and label (toggle -> toggle-input, toggle-label)
LUPAPISTE.ToggleModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  var prefix = params.prefix || "checkbox";

  self.id = _.uniqueId( params.prefix + "-toggle-" );
  self.value = params.value;
  // Unwrapping just in case (custom elements and implicit computeds)
  self.testId = ko.unwrap(params.testId) || "toggle";

  self.text = params.ltext ? loc( params.ltext ) : params.text || "";

  self.wrapperClass = prefix + "-wrapper";
  self.labelClass = prefix + "-label";
};
