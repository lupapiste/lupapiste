LUPAPISTE.DocgenDateModel = function( params ) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "default-docgen-date-template";

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));
  self.dateInputId = _.uniqueId( "date-input-");

  self.bindDatepicker = function() {
    if( !self.readonly() ) {
      $("#" + self.dateInputId).datepicker($.datepicker.regional [loc.getCurrentLanguage()]);
    }
  };
};
