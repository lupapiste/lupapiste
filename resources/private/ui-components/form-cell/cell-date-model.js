LUPAPISTE.CellDateModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.CellModel( params ) );

  self.attributes = _.defaults( self.attributes, {placeholder: loc( "choose")});
  // Dummy observable for the datepicker. Instead of this value we
  // parse the date ourselves from date. However, we make dummy a
  // call-through to the date in order to set the initial value.
  self.dummy = ko.computed({
    read: function() {
      return self.value();
    },
    write: _.noop
  });
};
