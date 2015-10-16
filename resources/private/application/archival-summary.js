(function() {
  "use strict";

  function GroupModel(groupName, groupDesc, attachments) {
    var self = this;
    self.attachments = ko.observableArray(attachments);
    self.groupName = groupName;
    self.groupDesc = groupDesc;
    // computed name, depending if attachments belongs to operation or not
    self.name = ko.computed( function() {
      if ( loc.hasTerm(["operations", self.groupName]) ) {
        if ( self.groupDesc ) {
          return loc(["operations", self.groupName]) + " - " + self.groupDesc;
        } else {
          return loc(["operations", self.groupName]);
        }
      } else {
        return loc(self.groupName);
      }
    });
  }

  var getPreAttachments = function(attachments) {
    return _.filter(attachments, function(attachment) {
      return !_.contains(LUPAPISTE.config.postVerdictStates, ko.unwrap(attachment.applicationState));
    });
  };

  var getPostAttachments = function(attachments) {
    return _.filter(attachments, function(attachment) {
      return _.contains(LUPAPISTE.config.postVerdictStates, ko.unwrap(attachment.applicationState));
    });
  };

  var filterByArchiveStatus = function(attachments, keepArchived) {
    return _.filter(attachments, function(attachment) {
      if (!attachment.metadata() || !attachment.metadata()["sailytysaika"] || !attachment.metadata()["sailytysaika"]["arkistointi"]()
        || attachment.metadata()["sailytysaika"]["arkistointi"]() === 'ei') {
        return !keepArchived;
      } else {
        return keepArchived;
      }
    });
  };

  var generalAttachmentsStr = "attachments.general";

  var getGroupList = function(attachments) {
    if (_.isEmpty(attachments)) return [];
    var grouped = _.groupBy(attachments, function(attachment) {
      return _.isObject(attachment.op) && attachment.op.id ? attachment.op.id() : generalAttachmentsStr;
    });
    var mapped = _.map(grouped, function(attachments, group) {
      if (group === generalAttachmentsStr) {
        return new GroupModel(group, null, attachments);
      } else {
        var att = _.first(attachments);
        return new GroupModel(ko.unwrap(att.op.name), ko.unwrap(att.op.description), attachments);
      }
    });
    return _.sortBy(mapped, function(group) { // attachments.general on top, else sort by op.created
      if ( group.groupName === generalAttachmentsStr ) {
        return -1;
      } else {
        return (_.first(group.attachments())).op.created();
      }
    });
  };

  var addAdditionalFieldsToAttachments = function(attachments, applicationId) {
    return _.map(attachments, function(attachment) {
      if (!_.isFunction(attachment.metadata)) {
        attachment.metadata = ko.observable(attachment.metadata);
      }
      attachment.showAdditionalControls = ko.observable(false);
      attachment.retentionDescription = ko.pureComputed(function() {
        var retention = attachment.metadata() ? attachment.metadata()["sailytysaika"] : null;
        if (retention && retention["arkistointi"]()) {
          var retentionMode = retention["arkistointi"]();
          var additionalDetail = "";
          switch(retentionMode) {
            case "toistaiseksi":
              additionalDetail = ", " + loc("laskentaperuste") + " " + loc(retention["laskentaperuste"]());
              break;
            case "m\u00E4\u00E4r\u00E4ajan":
              additionalDetail = ", " + retention["pituus"]() + " " + loc("vuotta");
              break;
          }
          return loc(retentionMode) + additionalDetail.toLowerCase();
        } else {
          return loc("<Arvo puuttuu>");
        }
      });
      attachment.personalDataDescription = ko.pureComputed(function() {
        var personalData = attachment.metadata() ? attachment.metadata()['henkilotiedot'] : null;
        if (_.isFunction(personalData)) {
          return loc(personalData());
        } else {
          return loc("<Arvo puuttuu>");
        }
      });
      if (attachment.type) {
        attachment.attachmentType = ko.observable([attachment.type['type-group'](), attachment.type['type-id']()].join('.'));
        attachment.attachmentType.subscribe(function (value) {
          ajax
            .command("set-attachment-type",
            {id: applicationId, attachmentId: attachment.id(), attachmentType: value})
            .success(function() {
              repository.load(applicationId);
            })
            .error(function(e) {
              repository.load(applicationId);
              error(e.text);
            })
            .call();
        });
      }
      return attachment;
    });
  };

  var collectMainDocuments = function(application) {
    return [{documentNameKey: 'applications.application', metadata: application.metadata}];
  };

  var model = function(params) {
    var self = this;
    self.attachments = params.application.attachments;
    var preAttachments = ko.pureComputed(function() {
      return addAdditionalFieldsToAttachments(getPreAttachments(self.attachments()), params.application.id());
    });
    var postAttachments = ko.pureComputed(function() {
      return addAdditionalFieldsToAttachments(getPostAttachments(self.attachments()), params.application.id());
    });
    self.archivedGroups = ko.pureComputed(function() {
      return getGroupList(filterByArchiveStatus(preAttachments(), true));
    });
    self.archivedPostGroups = ko.pureComputed(function() {
      return getGroupList(filterByArchiveStatus(postAttachments(), true));
    });
    self.notArchivedGroups = ko.pureComputed(function() {
      return getGroupList(filterByArchiveStatus(preAttachments(), false));
    });
    self.notArchivedPostGroups = ko.pureComputed(function() {
      return getGroupList(filterByArchiveStatus(postAttachments(), false));
    });
    var mainDocuments = ko.pureComputed(function() {
      return addAdditionalFieldsToAttachments(collectMainDocuments(params.application));
    });
    self.archivedDocuments = ko.pureComputed(function() {
      return filterByArchiveStatus(mainDocuments(), true);
    });
    self.notArchivedDocuments = ko.pureComputed(function() {
      return filterByArchiveStatus(mainDocuments(), false);
    });
    self.showArchived = ko.pureComputed(function() {
      return !_.isEmpty(self.archivedDocuments()) || !_.isEmpty(self.archivedGroups()) || !_.isEmpty(self.archivedPostGroups());
    });
    self.showNotArchived = ko.pureComputed(function() {
      return !_.isEmpty(self.notArchivedDocuments()) || !_.isEmpty(self.notArchivedGroups()) || !_.isEmpty(self.notArchivedPostGroups());
    });

    var attachmentGroupLabel = function(groupName) {
      return loc(["attachmentType", groupName, "_group_label"].join("."));
    };

    var attachmentTypeLabel = function(groupName, typeName) {
      return loc(["attachmentType", [groupName, typeName].join('.')].join("."));
    };

    self.selectableAttachmentTypes = ko.pureComputed(function () {
      return _.map(lupapisteApp.models.application.allowedAttachmentTypes(), function (typeGroup) {
        return {
          groupLabel: attachmentGroupLabel(typeGroup[0]),
          types: _.map(typeGroup[1], function (type) {
            return {
              typeLabel: attachmentTypeLabel(typeGroup[0], type),
              typeValue: [typeGroup[0], type].join('.')
            };
          })
        };
      });
    });
  };

  ko.components.register("archival-summary", {
    viewModel: model,
    template: {element: "archival-summary-template"}
  });

})();
