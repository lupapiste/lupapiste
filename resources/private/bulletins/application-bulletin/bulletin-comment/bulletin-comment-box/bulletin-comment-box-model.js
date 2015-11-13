LUPAPISTE.BulletinCommentBoxModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.bulletinId = params.bulletinId;
  self.versionId = params.versionId;
  self.comment = ko.observable();
  self.attachments = ko.observableArray([]);
  self.pending = ko.observable(false);

  self.email = ko.observable().extend({email: true});
  self.emailPreferred = ko.observable();

  self.userInfo = params.userInfo;

  self.otherReceiver = ko.observable(false);

  // User can select different receiving address for verdict messages i.e. solicitor
  self.otherReceiverInfo = {
    firstName: ko.observable(),
    lastName: ko.observable(),
    street: ko.observable(),
    zip: ko.observable(),
    city: ko.observable(),
    email: ko.observable().extend({email: true}),
    emailPreferred: ko.observable()
  };

  self.otherReceiverInfo.emailIsBlank = ko.pureComputed(function() {
    return _.isBlank(self.otherReceiverInfo.email());
  });

  ko.computed(function() {
    self.otherReceiverInfo.emailPreferred(!self.otherReceiverInfo.emailIsBlank());
  });

  self.emailIsBlank = ko.pureComputed(function() {
    return _.isBlank(self.email());
  });

  ko.computed(function() {
    self.emailPreferred(!_.isBlank(self.email()));
  });

  self.isDisabled = ko.pureComputed(function() {
    return self.pending() || !self.comment() || !self.email.isValid();
  });

  self.fileChanged = function(data, event) {
    self.attachments.push(util.getIn(_.first($(event.target)), ["files", 0]));
  };

  self.addAttachment = function(data, event) {
    $(event.target).closest("form").find("input[type='file']").click();
  };

  self.removeAttachment = function(attachment) {
    self.attachments.remove(attachment);
  };

  self.sendComment = function(form) {
    hub.send("bulletinService::newComment", {commentForm: form, files: self.attachments()});
  };

  self.addEventListener("bulletinService", "commentProcessed", function(event) {
    if (event.status === "success") {
      self.comment("");
      self.attachments([]);
      hub.send("indicator", {style: "positive", message: "bulletin.comment.save.success"});
    } else {
      hub.send("indicator", {style: "negative", message: "bulletin.comment.save.failed"});
    }
  });

  self.addEventListener("bulletinService", "commentProcessing", function(event) {
    var state = event.state;
    self.pending(state === "pending");
  });
};
