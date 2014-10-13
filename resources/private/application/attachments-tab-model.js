LUPAPISTE.AttachmentsTabModel = function(appModel) {
  "use strict";

  var self = this;

  self.appModel = appModel;

  var postVerdictStates = {verdictGiven:true, constructionStarted:true, closed:true};
  self.postVerdict = ko.observable(false);
  
  self.preAttachmentsByGroup = ko.observableArray();
  self.postAttachmentsByGroup = ko.observableArray();

  self.unsentAttachmentsNotFound = ko.observable(false);
  self.sendUnsentAttachmentsButtonDisabled = ko.computed(function() {
    return self.appModel.pending() || self.appModel.processing() || self.unsentAttachmentsNotFound();
  });

    
  function getPreAttachmentsByGroup(source) {
    return getAttachmentsByGroup(
      _.filter(source, function(attachment) {
          return !postVerdictStates[attachment.applicationState];
      }));
  }

  function getPostAttachmentsByGroup(source) {
    return getAttachmentsByGroup(
      _.filter(source, function(attachment) {
          return postVerdictStates[attachment.applicationState];
      }));
  }

  function getAttachmentsByGroup(source) {
    var attachments = _.map(source, function(a) {
      a.latestVersion = _.last(a.versions || []);
      a.statusName = LUPAPISTE.statuses[a.state] || "unknown";
      return a;
    });
    var grouped = _.groupBy(attachments, function(attachment) { return attachment.type['type-group']; });
    return _.map(grouped, function(attachments, group) { return {group: group, attachments: attachments}; });
  }

  function unsentAttachmentFound() {
    return _.some(ko.mapping.toJS(appModel.attachments), function(a) {
      var lastVersion = _.last(a.versions);
      return lastVersion &&
             (!a.sent || lastVersion.created > a.sent) &&
             (!a.target || (a.target.type !== "statement" && a.target.type !== "verdict"));
    });
  }

  self.refresh = function(appModel) {
    self.appModel = appModel;

    // Pre-verdict attachments
    self.preAttachmentsByGroup(getPreAttachmentsByGroup(ko.mapping.toJS(appModel.attachments)));

    // Post-verdict attachments
    self.postAttachmentsByGroup(getPostAttachmentsByGroup(ko.mapping.toJS(appModel.attachments)));

    // Post/pre verdict state?
    self.postVerdict(postVerdictStates[self.appModel.state()]);

    self.unsentAttachmentsNotFound(!unsentAttachmentFound());
  }

  self.sendUnsentAttachmentsToBackingSystem = function() {
    ajax
      .command("move-attachments-to-backing-system", {id: self.appModel.id(), lang: loc.getCurrentLanguage()})
      .success(self.appModel.reload)
      .processing(self.appModel.processing)
      .pending(self.appModel.pending)
      .call();
  };

  self.newAttachment = function() {
    attachment.initFileUpload(self.appModel.id(), null, null, true);
  };

  self.copyOwnAttachments = function(model) {
    ajax.command("copy-user-attachments-to-application", {id: self.appModel.id()})
      .success(self.appModel.reload)
      .processing(self.appModel.processing)
      .call();
    return false;
  };

  self.deleteSingleAttachment = function(a) {
    var attId = _.isFunction(a.id) ? a.id() : a.id;
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("attachment.delete.header"),
      loc("attachment.delete.message"),
      {title: loc("yes"),
       fn: function() {
        ajax.command("delete-attachment", {id: self.appModel.id(), attachmentId: attId})
          .success(function() {
            self.appModel.reload();
          })
          .processing(self.appModel.processing)
          .call();
        return false;
      }},
      {title: loc("no")});
  };

  self.attachmentTemplatesModel = new function() {
    var templateModel = this;
    templateModel.ok = function(ids) {
      ajax.command("create-attachments", {id: self.appModel.id(), attachmentTypes: ids})
        .success(function() { repository.load(self.appModel.id()); })
        .complete(LUPAPISTE.ModalDialog.close)
        .call();
    };

    templateModel.init = function() {
      templateModel.selectm = $("#dialog-add-attachment-templates .attachment-templates").selectm();
      templateModel.selectm.ok(templateModel.ok).cancel(LUPAPISTE.ModalDialog.close);
      return templateModel;
    };

    templateModel.show = function() {
      var data = _.map(this.allowedAttachmentTypes(), function(g) {
        var groupId = g[0];
        var groupText = loc(["attachmentType", groupId, "_group_label"]);
        var attachemntIds = g[1];
        var attachments = _.map(attachemntIds, function(a) {
          var id = {"type-group": groupId, "type-id": a};
          var text = loc(["attachmentType", groupId, a]);
          return {id: id, text: text};
        });
        return [groupText, attachments];
      });
      templateModel.selectm.reset(data);
      LUPAPISTE.ModalDialog.open("#dialog-add-attachment-templates");
      return templateModel;
    };
  }();
};
