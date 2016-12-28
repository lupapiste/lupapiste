LUPAPISTE.AttachmentsRequireBubbleModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;

  self.bubbleVisible = params.bubbleVisible;

  self.selectedTypes = ko.observableArray();

  self.selectedGroup = ko.observable();

  self.submitEnabled = self.disposedPureComputed(function() {
    return !_.isEmpty(self.selectedTypes());
  });

  self.requireAttachmentTemplates = function() {
    service.createAttachmentTemplates(_.map(self.selectedTypes(), function(type) {
      return _.pick(type, ["type-group", "type-id"]);
    }), self.selectedGroup());
    self.selectedTypes([]);
    self.bubbleVisible(false);
  };

};
