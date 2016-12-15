// Component for various checkbox wrappers.
// The wrappers are defined in _inputs.scss.
// Parameters [optional]:
//  value: Value observable
//  [text]:  Label text.
//  [prefix]: Wrapper prefix (e.g, checkbox, signbox, sectionbox, ... ). (default checkbox)
//  ltext: Label ltext (cannot be observable). Ltext overrides text if both are given.
//  [enable]: Similar to kO binding (default true)
//  [disable]: Similar to binding (false)
//  Toggle is enabled when disable is false and enable is true.
LUPAPISTE.ToggleModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var prefix = params.prefix || "checkbox";

  self.id = _.uniqueId( params.prefix + "-toggle-" );
  self.value = params.value;

  self.isDisabled = self.disposedPureComputed( function() {
    return ko.unwrap( params.disable )
      || (!_.isUndefined( params.enable ) && !ko.unwrap( params.enable ));
  });

  self.text = params.ltext ? loc( params.ltext ) : params.text || "";

  self.wrapperClass = prefix + "-wrapper";
  self.labelClass = prefix + "-label";
};
