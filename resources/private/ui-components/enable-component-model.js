// Extends ComponentBaseModel with optional enable and disable parameters:
//  [enable]: Similar to kO binding (default true)
//  [disable]: Similar to binding (false)
//  Toggle is enabled when disable is false and enable is true.
LUPAPISTE.EnableComponentModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel( params ));

  self.isDisabled = self.disposedPureComputed( function() {
    return ko.unwrap( params.disable )
        || ( _.has( params, "enable") && !ko.unwrap( params.enable ));
  });

};
