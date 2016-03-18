LUPAPISTE.VerdictAppealBubbleModel = function( params ) {
  "use strict";
  var self = this;

  // var service = lupapisteApp.services.fileUploadService;
  // self.fileInputId = service.fileInputId;
  // console.log( "fileInputid:", self.fileInputId);
  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.params = params;
  self.authors = ko.observable();

  self.date = ko.observable( "20.1.2022");

  // Dummy observable for the datepicker. Instead of this value we
  // parse the date ourselves from date. However, we make dummy a
  // call-through to the date in order to set the initial value.
  self.dummy = self.disposedComputed({
    read: function() {
      return self.date();
    },
    write: _.noop
  });

  self.files = ko.observableArray();

  function getMoment() {
    return moment( _.trim(self.date()), "D.M.YYYY", true);
  }

  self.dateWarning = self.disposedComputed( function() {
    return !_.trim( self.date())
      || getMoment().isValid()
      ? "" : "error.invalid-date";
  });
};
