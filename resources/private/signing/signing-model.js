LUPAPISTE.SigningModel = function(dialogSelector, confirmSuccess) {
  "use strict";
  var self = this;
  self.dialogSelector = dialogSelector;
  self.confirmSuccess = confirmSuccess;
  self.application = null;
  self.password = ko.observable("");
  self.attachments = ko.observable([]);
  self.selectedAttachments = ko.computed(function() { return _.filter(self.attachments(), function(a) {return a.selected();}); });
  self.processing = ko.observable(false);
  self.pending = ko.observable(false);
  self.errorMessage = ko.observable("");

  function normalizeAttachment(a) {
    var versions = _(a.versions).reverse().value(),
        latestVersion = versions[0];

    return {
      id:           a.id,
      type:         { "type-group": a.type["type-group"], "type-id": a.type["type-id"] },
      contentType:  latestVersion.contentType,
      filename:     latestVersion.filename,
      version:      { major: latestVersion.version.major, minor: latestVersion.version.minor },
      size:         latestVersion.size,
      selected:     ko.observable(true)
    };
  }

  self.init = function(application) {
    var app = ko.toJS(application);
    var attachments = _(app.attachments || []).filter(function(a) {return a.versions && a.versions.length;}).map(normalizeAttachment).value();

    self.application = app;
    self.password("");
    self.processing(false);
    self.pending(false);
    self.errorMessage("");
    self.attachments(attachments);
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  };

  self.sign = function() {
    self.errorMessage("");
    var id = self.application.id;
    var attachmentIds = _.map(self.selectedAttachments(), "id");
    if (attachmentIds && attachmentIds.length) {
      ajax.command("sign-attachments", {id: id, attachmentIds: attachmentIds, password: self.password()})
        .processing(self.processing)
        .pending(self.pending)
        .success(function() {
          self.password("");
          repository.load(id);
          LUPAPISTE.ModalDialog.close();
          if (self.confirmSuccess) {
            LUPAPISTE.ModalDialog.showDynamicOk(loc("application.signAttachments"), loc("signAttachment.ok"));
          }
        })
        .error(function(e) {self.errorMessage(e.text);})
        .call();
    }
  };

  function selectAllAttachments(value) {
    _.each(self.attachments(), function(f) { f.selected(value); });
  }

  self.selectAll = _.partial(selectAllAttachments, true);
  self.selectNone = _.partial(selectAllAttachments, false);

  var hubId = hub.subscribe( "sign-attachments", _.flow( _.partialRight( _.get, "application"), self.init ) );

  self.dispose = _.partial(hub.unsubscribe, hubId);

};
