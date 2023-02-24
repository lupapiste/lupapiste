// Like InputFieldModel but with an extra optional parameter:
// immediate: if true, then the changes are notified immediately,
// otherwise on blur (default true)
LUPAPISTE.TextFieldModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.InputFieldModel( params));

  self.isImmediate = Boolean( _.get( params, "immediate", true ));
};
