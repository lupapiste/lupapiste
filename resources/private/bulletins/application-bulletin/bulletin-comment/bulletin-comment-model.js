LUPAPISTE.BulletinCommentModel = function() {
  "use strict";
  var self = this;

  self.comment = ko.observable();

  self.attachments = ko.observableArray([]);

  var componentForm = null;

  self.fileChanged = function(data, event) {
    self.attachments.push(util.getIn(_.first($(event.target)), ["files", 0]));
  };

  self.addAttachment = function(data, event) {
    $(event.target).closest("form").find("input[type='file']").click();
  };

  self.removeAttachment = function(attachment) {
    console.log("remove", attachment);
    self.attachments.remove(attachment);
  };

  self.onSuccess = function() {
    if (componentForm) {
      componentForm.reset();
    }
    self.attachments([]);
  };

  self.sendComment = function(form) {
    componentForm = form;
    hub.send("bulletinService::sendComment", {commentForm: form, files: self.attachments(), onSuccess: self.onSuccess});
  };
};
