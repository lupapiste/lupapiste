// Docgen wrapper for jQuery UI datepicker.
// Note: In case you are wondering, why the template does not have the
// typical label - input relationship (for - id) the reason is that
// datepicker reserves id and overriding it throws exception.
LUPAPISTE.DocgenDateModel = function( params ) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "default-docgen-date-template";

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));

  self.datepickerValue = ko.observable(self.value());

  self.datepickerOptions = $.datepicker.regional[loc.getCurrentLanguage()];

  self.placeholder = self.schema.placeholder;
};
