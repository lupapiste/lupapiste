LUPAPISTE.VerdictAppealBubbleModel = function( params ) {
  "use strict";
  var self = this;

  // var service = lupapisteApp.services.fileUploadService;
  // self.fileInputId = service.fileInputId;
  // console.log( "fileInputid:", self.fileInputId);
    ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.params = params;

  self.date = ko.observable();
  self.files = ko.observableArray();
};
