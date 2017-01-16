LUPAPISTE.BulletinCommentBoxModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.bulletinId = params.bulletinId;
  self.versionId = params.versionId;
  self.attachments = ko.observableArray([]);
  self.pending = ko.observable(false);
  self.filePending = ko.observable(false);

  self.basicCommentFields = {
    comment: ko.observable(),
    email: ko.observable().extend({email: true}),
    emailPreferred: ko.observable()
  };

  self.userInfo = params.userInfo;

  self.otherReceiver = ko.observable(false);

  self.fileInputId = params.fileInputId;

  ko.computed(function() {
    if (!self.otherReceiver()) {
      _.mapKeys(self.otherReceiverInfo, function(value, key) {
        self.otherReceiverInfo[key](undefined);
        self.otherReceiverInfo[key].isModified(false);
      });
    }
    self.basicCommentFields.email(undefined);
    self.basicCommentFields.emailPreferred(undefined);
  });

  // User can select different receiving address for verdict messages i.e. solicitor
  self.otherReceiverInfo = {
    firstName: ko.observable(),
    lastName: ko.observable(),
    street: ko.observable(),
    zip: ko.observable(),
    city: ko.observable()
  };

  self.allOtherInfo = ko.validatedObservable([self.otherReceiverInfo.firstName, self.otherReceiverInfo.lastName,
                                              self.otherReceiverInfo.street, self.otherReceiverInfo.zip, self.otherReceiverInfo.city]);

  self.emailIsBlank = ko.pureComputed(function() {
    return _.isBlank(self.basicCommentFields.email());
  });

  ko.computed(function() {
    self.basicCommentFields.emailPreferred(!_.isBlank(self.basicCommentFields.email()));
  });

  self.isSubmitDisabled = ko.pureComputed(function() {
    var allOtherInfoIsValid = self.otherReceiver() ? self.allOtherInfo.isValid() : true;
    var isPending = self.pending() || self.filePending();
    return isPending || !self.basicCommentFields.comment() || !self.basicCommentFields.email.isValid() || !allOtherInfoIsValid;
  });

  self.removeAttachment = function(attachment) {
    hub.send("fileuploadService::removeFile", {attachmentId: attachment.fileId});
  };

  self.sendComment = function() {
    var comment = self.otherReceiver() ?
      _.merge(ko.toJS(self.basicCommentFields), { otherReceiver: ko.toJS(self.otherReceiverInfo) }) :
      ko.toJS(self.basicCommentFields);
    comment.files = self.attachments();
    comment.bulletinId = self.bulletinId();
    comment.bulletinVersionId = self.versionId();

    hub.send("bulletinService::newComment", comment);
  };

  self.addEventListener("fileuploadService", "filesUploading", function(event) {
    self.filePending(event.state === "pending");
  });

  self.addEventListener("fileuploadService", "filesUploaded", function(event) {
    if(event.status === "success") {
      self.attachments(self.attachments().concat(event.files));
    } else {
      hub.send("indicator", {
        style: "negative",
        message: event.message
      });
    }
  });

  self.addEventListener("fileuploadService", "fileRemoved", function(event) {
    self.attachments.remove(function(attachment) {
      return attachment.fileId === event.attachmentId;
    });
  });

  self.addEventListener("fileuploadService", "badFile", function(event) {
    hub.send("indicator", {
      style: "negative",
      message: event.message
    });
  });

  self.addEventListener("bulletinService", "commentProcessed", function(event) {
    if (event.status === "success") {
      self.basicCommentFields.comment("");
      self.attachments([]);
      hub.send("indicator", {style: "positive", message: "bulletin.comment.save.success", sticky: true});
    } else {
      hub.send("indicator", {style: "negative", message: "bulletin.comment.save.failed"});
    }
    self.pending(false);
  });

  self.addEventListener("bulletinService", "commentProcessing", function() {
    self.pending(true);
  });
};

