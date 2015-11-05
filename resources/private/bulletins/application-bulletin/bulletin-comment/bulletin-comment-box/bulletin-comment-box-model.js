LUPAPISTE.BulletinCommentBoxModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.bulletinId = params.bulletinId;
  self.comment = ko.observable();
  self.attachments = ko.observableArray([]);
  self.pending = ko.observable(false);

  self.fileChanged = function(data, event) {
    self.attachments.push(util.getIn(_.first($(event.target)), ["files", 0]));
  };

  self.addAttachment = function(data, event) {
    $(event.target).closest("form").find("input[type='file']").click();
  };

  self.removeAttachment = function(attachment) {
    self.attachments.remove(attachment);
  };

  self.onSuccess = function() {
    self.comment("");
    self.attachments([]);
  };

  self.sendComment = function(form) {
    hub.send("bulletinService::sendComment", {commentForm: form, files: self.attachments()});
  };

  self.addEventListener("bulletinService", "commentSubmitted", self.onSuccess);

  self.addEventListener("bulletinService", "commentPending", function(event) {
    self.pending(event.value);
  });
};
