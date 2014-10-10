LUPAPISTE.AttachmentsTabModel = function(postVerdictStates, appId) {
  "use strict";

  var self = this;

  self.applicationId = appId;
  self.postVerdictStates = postVerdictStates;
  
  self.preAttachmentsByGroup = ko.observableArray();
  self.postAttachmentsByGroup = ko.observableArray();
    
  function getPreAttachmentsByGroup(source) {
    return getAttachmentsByGroup(
      _.filter(source, function(attachment) {
          return !self.postVerdictStates[attachment.applicationState];
      }));
  }

  function getPostAttachmentsByGroup(source) {
    return getAttachmentsByGroup(
      _.filter(source, function(attachment) {
          return self.postVerdictStates[attachment.applicationState];
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

  self.refresh = function(attachments) {
    // Pre-verdict attachments
    self.preAttachmentsByGroup(getPreAttachmentsByGroup(attachments));

    // Post-verdict attachments
    self.postAttachmentsByGroup(getPostAttachmentsByGroup(attachments));
  }

  self.attachmentTemplatesModel = new function() {
    var templateModel = this;
    templateModel.ok = function(ids) {
      ajax.command("create-attachments", {id: self.applicationId(), attachmentTypes: ids})
        .success(function() { repository.load(self.applicationId()); })
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
