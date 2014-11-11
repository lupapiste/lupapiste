LUPAPISTE.AttachmentsTabModel = function(appModel) {
  "use strict";

  var self = this;

  self.appModel = appModel;

  self.stampingMode = ko.observable(false);

  var postVerdictStates = {verdictGiven:true, constructionStarted:true, closed:true};

  self.preAttachmentsByOperation = ko.observableArray();
  self.postAttachmentsByOperation = ko.observableArray();

  self.unsentAttachmentsNotFound = ko.observable(false);
  self.sendUnsentAttachmentsButtonDisabled = ko.computed(function() {
    return self.appModel.pending() || self.appModel.processing() || self.unsentAttachmentsNotFound();
  });

  self.showHelp = ko.observable(false);

  self.stampButtonStr = ko.computed(function() {
    return self.stampingMode() ? 'cancel' : 'application.stampAttachments';
  });
  var generalAttachmentsStr = 'attachments.general';

  function GroupModel(groupName, groupDesc, attachments, editable) {
    var self = this;
    self.attachments = attachments;
    self.groupName = groupName;
    self.groupDesc = groupDesc;
    self.editable = editable;
    // computed name, depending if attachments belongs to operation or not
    self.name = ko.computed( function() {
      if ( loc.hasTerm(['operations', self.groupName]) ) {
        if ( self.groupDesc ) {
          return loc(['operations', self.groupName]) + ' - ' + self.groupDesc;
        } else {
          return loc(['operations', self.groupName]);
        }
      } else {
        return loc(self.groupName); // 'attachments.general'
      }
    });
  };

  var fGroupByOperation = function(attachment) {
    return attachment.op ? attachment.op['id'] : generalAttachmentsStr;
  };

  /* Sorting function to sort attachments into
   * same order as in allowedAttachmentTypes -observable
   */
  var fSortByAllowedAttachmentType = function(a, b) {
    var types = _.flatten(self.appModel.allowedAttachmentTypes(), true);

    var atg = a.type['type-group'];
    var atgIdx = _.indexOf(types, atg);
    var atid = a.type['type-id'];

    var btg = b.type['type-group'];
    var btgIdx = _.indexOf(types, btg);
    var btid = b.type['type-id'];

    if ( atg === btg ) {
      // flattened array of allowed attachment types.
      // types[atgIdx + 1] is array of type-ids,
      // which correnspond to type-group in atgIdx
      return _.indexOf(types[atgIdx + 1], atid) - _.indexOf(types[btgIdx + 1], btid);
    } else {
      return atgIdx - btgIdx;
    }
  };

  function getPreAttachments(source) {
    return _.filter(source, function(attachment) {
          return !postVerdictStates[attachment.applicationState];
      });
  }

  function getPostAttachments(source) {
    return _.filter(source, function(attachment) {
          return postVerdictStates[attachment.applicationState];
      });
  }

  /*
   * Returns attachments (source), grouped by grouping function f.
   * Optionally sorts using sort
   */
  function getAttachmentsByGroup(source, f, sort, editable) {
    var attachments = _.map(source, function(a) {
      a.latestVersion = _.last(a.versions || []);
      a.statusName = LUPAPISTE.statuses[a.state] || "unknown";
      return a;
    });
    if ( _.isFunction(sort) ) {
      attachments.sort(sort);
    }
    var grouped = _.groupBy(attachments, f);
    var mapped = _.map(grouped, function(attachments, group) {
      if ( group === generalAttachmentsStr ) {
        return new GroupModel(group, null, attachments, editable); // group = attachments.general
      } else { // group == op.id
        var att = _.first(attachments);
        return new GroupModel(att.op.name, att.op.description, attachments, editable);
      }
    });
    return _.sortBy(mapped, function(group) { // attachments.general on top, else sort by op.created
      if ( group.groupName === generalAttachmentsStr ) {
        return -1;
      } else {
        return (_.first(group.attachments)).op.created;
      }
    });
  }

  function unsentAttachmentFound(attachments) {
    return _.some(attachments, function(a) {
      var lastVersion = _.last(a.versions);
      return lastVersion &&
             (!a.sent || lastVersion.created > a.sent) &&
             (!a.target || (a.target.type !== "statement" && a.target.type !== "verdict"));
    });
  }

  self.toggleHelp = function() {
    self.showHelp(!self.showHelp());
  };

  self.refresh = function(appModel) {
    self.appModel = appModel;
    var rawAttachments = ko.mapping.toJS(appModel.attachments);

    var preAttachments = getPreAttachments(rawAttachments);
    var postAttachments = getPostAttachments(rawAttachments);

    // pre verdict attachments are not editable after verdict has been given
    var preGroupEditable = currentUser.isAuthority() || !postVerdictStates[appModel.state()];
    var preGrouped = getAttachmentsByGroup(preAttachments, fGroupByOperation, fSortByAllowedAttachmentType, preGroupEditable);
    var postGrouped = getAttachmentsByGroup(postAttachments, fGroupByOperation, fSortByAllowedAttachmentType, true);

    self.preAttachmentsByOperation(preGrouped);
    self.postAttachmentsByOperation(postGrouped);

    self.unsentAttachmentsNotFound(!unsentAttachmentFound(rawAttachments));
  };

  self.sendUnsentAttachmentsToBackingSystem = function() {
    ajax
      .command("move-attachments-to-backing-system", {id: self.appModel.id(), lang: loc.getCurrentLanguage()})
      .success(self.appModel.reload)
      .processing(self.appModel.processing)
      .pending(self.appModel.pending)
      .call();
  };

  self.newAttachment = function() {
    attachment.initFileUpload({
      applicationId: self.appModel.id(),
      attachmentId: null,
      attachmentType: null,
      typeSelector: true,
      opSelector: true
    });
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
          .error(function (e) {
            LUPAPISTE.ModalDialog.showDynamicOk(loc("error.dialog.title"), loc(e.text));;
          })
          .processing(self.appModel.processing)
          .call();
        return false;
      }},
      {title: loc("no")});
  };

  self.cancelStamping = function() {
    self.stampingMode(false);
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
      var data = _.map(self.appModel.allowedAttachmentTypes(), function(g) {
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

  hub.subscribe("op-description-changed", function(e) {
    var opid = e['op-id'];
    var desc = e['op-desc'];

    _.each(self.appModel.attachments(), function(attachment) {
      if ( ko.unwrap(attachment.op) && attachment.op.id() === opid ) {
        attachment.op.description(desc);
      }
    });

    self.refresh(self.appModel);
  });

  ko.components.register('stamping-component', {
    viewModel: LUPAPISTE.StampModel,
    template: {element: "dialog-stamp-attachments"}
  });
};
