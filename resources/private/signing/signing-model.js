LUPAPISTE.SigningModel = function() {
  "use strict";
  var self = this;
  self.application = null;
  self.password = ko.observable("");
  self.attachments = ko.observable(null);
  self.selectedAttachments = ko.computed(function() { return _.filter(self.attachments(), function(a) {return a.selected();}); });
  self.processing = ko.observable(false);
  self.pending = ko.observable(false);
  self.errorMessage = ko.observable("");

  function normalizeAttachment(a) {
    var versions = _(a.versions()).reverse().value(),
        latestVersion = versions[0];

    return {
      id:           a.id(),
      type:         { "type-group": a.type["type-group"](), "type-id": a.type["type-id"]() },
      contentType:  latestVersion.contentType(),
      filename:     latestVersion.filename(),
      version:      { major: latestVersion.version.major(), minor: latestVersion.version.minor() },
      size:         latestVersion.size(),
      selected:     ko.observable(true)
    };
  }

  self.init = function(application) {
    self.application = application;
    self.password("");
    self.processing(false);
    self.pending(false);
    self.errorMessage("");
    self.attachments(_(application.attachments()).filter(function(a) {return a.versions().length;}).map(normalizeAttachment).value());
    LUPAPISTE.ModalDialog.open("#dialog-sign-attachments");
  };

  self.sign = function() {
    self.errorMessage("");
    var data = {id: self.application.id(), attachmentIds: _.map(self.selectedAttachments(), "id"), password: self.password()};
    ajax.command("sign-attachments", data)
      .processing(self.processing)
      .pending(self.pending)
      .success(function() {
        self.password("");
        repository.load(self.application.id());
        // TODO notification?
        LUPAPISTE.ModalDialog.close();
      })
      .error(function(e) {self.errorMessage(e.text);})
      .call();
  };

  function selectAllAttachments(value) {
    _.each(self.attachments(), function(f) { f.selected(value); });
  }

  self.selectAll = _.partial(selectAllAttachments, true);
  self.selectNone = _.partial(selectAllAttachments, false);

  self.setSelectedAttachmentId = function(attachmentId) {
    self.selectNone();
    var a = _.find(self.attachments(), function(a) {return a.id() === attachmentId;});
    if (a) {
      a.selected(true);
    } else {
      error("Attachment not found", attachmentId);
    }
  };

};
