// Component for various checkbox wrappers.
// The wrappers are defined in _inputs.scss.
// Parameters [optional]:
//  value: Value observable
//  [text]:  Label text.
//  [prefix]: Wrapper prefix (e.g, checkbox, signbox, sectionbox, ... ). (default checkbox)
//  ltext: Label ltext (cannot be observable). Ltext overrides text if both are given.
//  [noText]: If true, the label does not contain text. However, the
//  text/ltext params are still used for aria-label (default false)
//  [testId]:  Test id prefix for input and label (toggle -> toggle-input, toggle-label)
//  [invalid]: Whether the current value is invalid. Can be observable.
//  [required]: Whether the toggel is required. Can be observable.
//  [callback]: Callback function that is called when toggle is
//              clicked. Receives the new toggled value as argument
//
// The above information is incorrect but dangerous to change:
// the callback receives the OLD value when called, not the new one
// (assuming a ticked checkbox is considered true)
LUPAPISTE.ToggleModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  var prefix = params.prefix || "checkbox";

  self.id = _.uniqueId( params.prefix + "-toggle-" );
  self.value = params.value;
  // Unwrapping just in case (custom elements and implicit computeds)
  self.testId = ko.unwrap(params.testId) || "toggle";

  self.ariaLabel = params.ltext ? loc( params.ltext ) : params.text || "";
  self.noText = params.noText || _.isBlank( ko.unwrap( self.ariaLabel ) );
  self.text = self.noText ? null : self.ariaLabel;
  self.isInvalid = ko.unwrap( params.invalid );
  self.isRequired = ko.unwrap( params.required );

  self.labelClass = prefix + "-label";

  self.wrapperCss = _.set( {"wrapper--no-border": self.noText,
                            "wrapper--no-label": self.noText},
                           prefix + "-wrapper", true);

  self.click = function() {
    if( params.callback ) {
      params.callback( !self.value() );
    }
    return true;
  };
};
