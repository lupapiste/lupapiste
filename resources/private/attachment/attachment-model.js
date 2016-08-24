LUPAPISTE.AttachmentModel = function(attachmentData, authModel) {
  "use strict";
  var self = _.assign(this, attachmentData);
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.authModel = authModel;

  self.service = lupapisteApp.services.attachmentsService;

  self.notNeeded = ko.observable(attachmentData.notNeeded);
  self.disposedSubscribe(self.notNeeded, function(val) {
    self.service.setNotNeeded(self.id, val, { onComplete: _.partial(self.service.queryOne, self.id) });
  });

  self.reset = function(attachmentData) {
    self.notNeeded(attachmentData.notNeeded);
    return _.assign(self, _.omit(attachmentData, ["notNeeded"]));
  };

};
