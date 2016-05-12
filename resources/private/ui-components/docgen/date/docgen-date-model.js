LUPAPISTE.DocgenDateModel = function( params ) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "default-docgen-date-template";

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));

  self.datepickerValue = ko.observable(self.value());

  self.disposedSubscribe(self.datepickerValue, function(value) {
    var dateStr = value ? moment(value).format("D.M.YYYY") : "";
    if (self.value() !== dateStr) {
      self.value(dateStr);
    }
  });

  self.datepickerOptions = $.datepicker.regional[loc.getCurrentLanguage()];

};
