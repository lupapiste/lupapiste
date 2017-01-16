// Convenience component for a button with icon.
// Parameters [optional]:
//  text: Button text
//  ltext: Button ltext (cannot be observable). Ltext overrides text if both are given.
//  icon: Lupicon without prefix: check -> lupicon-check.
//  [buttonClass]. Default positive.
//  click: Similar to KO binding
//  right: If true, the icon is on the right side (default false)
//  [testId]: Button test id (icon-button)
//  [enable]: Similar to kO binding (default true)
//  [disable]: Similar to binding (false)
//  [waiting]: Waiting observable (false)
//  Button is enabled when waiting and disable are false and enable is true.
LUPAPISTE.IconButtonModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.right = params.right || false;
  self.waiting = params.waiting || false;
  self.testId = params.testId || "icon-button";

  self.buttonClass = params.buttonClass || "positive";

  self.isDisabled = self.disposedPureComputed( function() {
    return ko.unwrap( self.waiting )
      || ko.unwrap( params.disable )
      || !ko.unwrap( params.enable || true );
  });

  self.click = params.click;
  self.iconClass = self.disposedPureComputed( function() {
    return ko.unwrap( self.waiting )
      ? "icon-spin lupicon-refresh"
      : "lupicon-" + ko.unwrap( params.icon );
  });

  self.buttonText = params.ltext ? loc( params.ltext ) : params.text;


};
