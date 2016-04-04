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
      return !_.includes(LUPAPISTE.config.postVerdictStates, ko.unwrap(attachment.applicationState)) &&
        attachment.latestVersion;
    });
  };

  var getPostAttachments = function(attachments) {
    return _.filter(attachments, function(attachment) {
      return _.includes(LUPAPISTE.config.postVerdictStates, ko.unwrap(attachment.applicationState)) &&
          attachment.latestVersion;
    });
  };

  var filterByArchiveStatus = function(attachments, keepArchived) {
    return _.filter(attachments, function(attachment) {
      if (!attachment.metadata() || !attachment.metadata().sailytysaika || !attachment.metadata().sailytysaika.arkistointi()
        || attachment.metadata().sailytysaika.arkistointi() === "ei") {
        return !keepArchived;
      } else {
        return keepArchived;
      }
    });
  };

  var generalAttachmentsStr = "attachments.general";

  var getGroupList = function(attachments) {
    if (_.isEmpty(attachments)) {
      return [];
    }
    var grouped = _.groupBy(attachments, function(attachment) {
      return _.isObject(attachment.op) && attachment.op.id ? attachment.op.id() : generalAttachmentsStr;
    });
    var mapped = _.map(grouped, function(attachments, group) {
      if (group === generalAttachmentsStr) {
        return new GroupModel(group, null, attachments);
      } else {
        var att = _.head(attachments);
        return new GroupModel(ko.unwrap(att.op.name), ko.unwrap(att.op.description), attachments);
      }
    });
    return _.sortBy(mapped, function(group) { // attachments.general on top, else sort by op.created
      if ( group.groupName === generalAttachmentsStr ) {
        return -1;
      } else {
        return (_.head(group.attachments())).op.created();
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
        var retention = attachment.metadata() ? attachment.metadata().sailytysaika : null;
        if (retention && retention.arkistointi()) {
          var retentionMode = retention.arkistointi();
          var additionalDetail = "";
          switch(retentionMode) {
            case "toistaiseksi":
              additionalDetail = ", " + loc("laskentaperuste") + " " + loc(retention.laskentaperuste());
              break;
            case "m\u00E4\u00E4r\u00E4ajan":
              additionalDetail = ", " + retention.pituus() + " " + loc("vuotta");
              break;
          }
          return loc(retentionMode) + additionalDetail.toLowerCase();
        } else {
          return loc("<Arvo puuttuu>");
        }
      });
      attachment.personalDataDescription = ko.pureComputed(function() {
        var personalData = attachment.metadata() ? attachment.metadata().henkilotiedot : null;
        if (_.isFunction(personalData)) {
          return loc(personalData());
        } else {
          return loc("<Arvo puuttuu>");
        }
      });
      if (attachment.type) {
        attachment.attachmentType = ko.observable([attachment.type["type-group"](), attachment.type["type-id"]()].join("."));
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
      attachment.archivable = util.getIn(attachment, ["latestVersion", "archivable"]) ? attachment.latestVersion.archivable() : false;
      attachment.archivabilityError = util.getIn(attachment, ["latestVersion", "archivabilityError"]) ? attachment.latestVersion.archivabilityError() : null;
      attachment.sendToArchive = ko.observable(false);
      attachment.state = util.getIn(attachment, ["metadata", "tila"]) ? attachment.metadata().tila : ko.observable();
      return attachment;
    });
  };

  var collectMainDocuments = function(application) {
    return [{documentNameKey: "applications.application", metadata: application.metadata, id: application.id() + "-application", previewAction: "pdf-export"},
            {documentNameKey: "caseFile.heading", metadata: application.processMetadata, id: application.id() + "-case-file", previewAction: "pdfa-casefile"}];
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
    var archivedPreAttachments = ko.pureComputed(function() {
      return filterByArchiveStatus(preAttachments(), true);
    });
    var archivedPostAttachments = ko.pureComputed(function() {
      return filterByArchiveStatus(postAttachments(), true);
    });
    self.archivedGroups = ko.pureComputed(function() {
      return getGroupList(archivedPreAttachments());
    });
    self.archivedPostGroups = ko.pureComputed(function() {
      return getGroupList(archivedPostAttachments());
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
      return loc(["attachmentType", [groupName, typeName].join(".")].join("."));
    };

    self.selectableAttachmentTypes = ko.pureComputed(function () {
      return _.map(lupapisteApp.models.application.allowedAttachmentTypes(), function (typeGroup) {
        return {
          groupLabel: attachmentGroupLabel(typeGroup[0]),
          types: _.map(typeGroup[1], function (type) {
            return {
              typeLabel: attachmentTypeLabel(typeGroup[0], type),
              typeValue: [typeGroup[0], type].join(".")
            };
          })
        };
      });
    });

    var isSelectedForArchive = function(attachment) {
      return ko.unwrap(attachment.sendToArchive);
    };

    self.archivingInProgressIds = ko.observableArray();

    self.archiveButtonEnabled = ko.pureComputed(function() {
      return _.isEmpty(self.archivingInProgressIds()) && (_.some(preAttachments(), isSelectedForArchive) ||
        _.some(postAttachments(), isSelectedForArchive) || _.some(mainDocuments(), isSelectedForArchive));
    });

    self.selectAll = function() {
      var selectIfArchivable = function(attachment) {
        if (attachment.archivable && attachment.state() !== "arkistoitu") {
          attachment.sendToArchive(true);
        }
      };
      _.forEach(archivedPreAttachments(), selectIfArchivable);
      _.forEach(archivedPostAttachments(), selectIfArchivable);
      _.forEach(self.archivedDocuments(), function(doc) {
        if (doc.state() !== "arkistoitu") {
          doc.sendToArchive(true);
        }
      });
    };

    var updateState = function(docs, stateMap) {
      _.forEach(docs, function(doc) {
        var id = ko.unwrap(doc.id);
        if (_.has(stateMap, id)) {
          var newState = stateMap[id];
          if (doc.metadata().tila) {
            doc.metadata().tila(newState);
          } else {
            doc.state(newState);
          }
          if (newState === "arkistoitu") {
            doc.sendToArchive(false);
          }
          if (newState !== "arkistoidaan") {
            self.archivingInProgressIds.remove(id);
          }
          if (newState === "arkistoidaan" && !_.includes(self.archivingInProgressIds(), id)) {
            self.archivingInProgressIds.push(id);
          }
        }
      });
    };

    var pollChangedState = function(documentIds) {
      ajax
        .query("document-states",
          {
            id: ko.unwrap(params.application.id),
            documentIds: JSON.stringify(documentIds)
          })
        .success(function(data) {
          updateState(mainDocuments(), data.state);
          updateState(archivedPreAttachments(), data.state);
          updateState(archivedPostAttachments(), data.state);
        })
        .call();
    };

    var pollArchiveStatus = function() {
      pollChangedState(self.archivingInProgressIds());
      if (!_.isEmpty(self.archivingInProgressIds())) {
        window.setTimeout(pollArchiveStatus, 2000);
      } else {
        repository.load(ko.unwrap(params.application.id));
      }
    };

    var getId = function(doc) {
      return ko.unwrap(doc.id);
    };

    var allIds = _.concat(
      _.map(mainDocuments(), getId),
      _.map(archivedPreAttachments(), getId),
      _.map(archivedPostAttachments(), getId)
    );

    pollChangedState(allIds);

    self.archiveSelected = function() {
      var attachmentIds = _.map(_.filter(self.attachments(), isSelectedForArchive), function(attachment) {
        return ko.unwrap(attachment.id);
      });
      var mainDocumentIds = _.map(_.filter(mainDocuments(), isSelectedForArchive), function(doc) {
        return ko.unwrap(doc.id);
      });
      self.archivingInProgressIds(attachmentIds.concat(mainDocumentIds));
      window.setTimeout(pollArchiveStatus, 5000);
      ajax
        .command("archive-documents",
          {
            id: ko.unwrap(params.application.id),
            attachmentIds: attachmentIds,
            documentIds: mainDocumentIds
          })
        .error(function(e) {
          error(e.text);
        })
        .call();
    };
  };

  ko.components.register("archival-summary", {
    viewModel: model,
    template: {element: "archival-summary-template"}
  });

})();
