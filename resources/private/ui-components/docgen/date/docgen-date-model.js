LUPAPISTE.DocgenDateModel = function( params ) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "default-docgen-date-template";

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));

  self.datepickerValue = ko.observable(self.value());

  self.datepickerOptions = $.datepicker.regional[loc.getCurrentLanguage()];

  self.placeholder = self.schema.placeholder ? loc( self.schema.placeholder ) : "";

};
