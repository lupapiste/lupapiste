(function() {
  "use strict";

  var attachmentsService = lupapisteApp.services.attachmentsService;
  var accordionService = lupapisteApp.services.accordionService;

  function operationDescription(operationId) {
    var doc = accordionService && accordionService.getDocumentByOpId(operationId);
    if (util.getIn(doc, ["operation"])) {
      var identifier = util.getIn(accordionService.getIdentifier(doc.docId), ["value"]);
      var opDescription = util.getIn(doc, ["operation", "description"]);
      var accordionFields = docutils.accordionText(doc.accordionPaths, doc.data);
      return _.first(_.filter([accordionFields, identifier, opDescription]));
    } else {
      return "";
    }
  }


  function GroupModel(groupName, groupDesc, attachments) {
    var self = this;

    ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

    var sortedAttachments = _.sortBy(attachments, function(att) {
        var localizedGroup = loc(["attachmentType", ko.unwrap(att.type)["type-group"], "_group_label"]);
        return localizedGroup;
    });
    self.attachments = ko.observableArray(sortedAttachments);
    self.groupName = groupName;
    var fullGroupDesc = groupDesc || "";
    // computed name, depending if attachments belongs to operation or not
    self.name = self.disposedComputed( function() {
      if (loc.hasTerm(["operations", self.groupName])) {
        return loc(["operations", self.groupName]) + fullGroupDesc;
      } else {
        return loc(self.groupName);
      }
    });
  }

  function isArchived(attachment) {
    var arkistointi = util.getIn(attachment, ["metadata", "sailytysaika", "arkistointi"]);
    return arkistointi && arkistointi !== "ei";
  }

  var generalAttachmentsStr = "attachments.general";

  var groupLists = {};

  function getGroupList(groupListId, attachments) {
    // Group list for attachments grouped by archived/not-archived and pre-/postverdict
    if (_.isEmpty(attachments)) {
      return [];
    }

    function getGroup(groupList, attachments, group) {
      if (!groupList.initializedGroups[group]) {
        var op = _.first(util.getIn(attachments, [0, "op"]));
        var opDescription = op ? operationDescription(op.id) : "";
        var groupModel = group === generalAttachmentsStr ? new GroupModel(group, null, attachments) : new GroupModel(op.name, opDescription, attachments);
        groupList.initializedGroups[group] = groupModel;
      } else {
        groupList.initializedGroups[group].attachments(attachments);
      }
      return groupList.initializedGroups[group];
    }

    var groupList = groupLists[groupListId] || { initializedGroups: {} };
    groupLists[groupListId] = groupList;

    return  _(attachments)
      .groupBy(function(attachment) {
        var op = util.getIn(attachment, ["op"]);
        if (_.isArray(op)) {
          op = op[0];
        }
        return util.getIn(op, ["id"]) || generalAttachmentsStr;
      })
      .map(_.partial(getGroup, groupList))
      .sortBy(function(group) { // attachments.general on top, else sort by op.created
        if ( group.groupName === generalAttachmentsStr ) {
          return -1;
        } else {
          return util.getIn(group.attachments, [0, "op", "created"]);
        }
      })

      .value();

  }

  function ArchivalSummaryDocumentModel(application, doc) {
    var self = _.assign(this, doc);

    ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

    self.authModel = attachmentsService.authModels()[doc.id] || lupapisteApp.models.applicationAuthModel;

    self.metadata = ko.observable(ko.unwrap(doc.metadata));
    self.attachmentType = ko.observable(ko.unwrap(doc.typeString));

    self.typeChange = util.getIn(doc, ["type", "type-group"]) && util.getIn(doc, ["type", "type-id"]) &&
      new LUPAPISTE.AttachmentsChangeTypeModel({ authModel: self.authModel,
                                                 allowedAttachmentTypes: lupapisteApp.models.application.allowedAttachmentTypes(),
                                                 attachmentType: self.type,
                                                 attachmentId: self.id });

    self.addEventListener("attachments", {eventType: "attachment-type-selected", attachmentId: doc.id}, function(data) {
      self.type(data.attachmentType);
    });

    self.showAdditionalControls = ko.observable(false);
    self.toggleAdditionalControls = function() {
      self.showAdditionalControls(!self.showAdditionalControls());
    };

    self.archivable = ko.observable();
    self.archivabilityError = ko.observable();

    self.sendToArchive = ko.observable(false);
    self.archived = ko.observable(false);

    self.showConversionLog = ko.observable(false);
    self.convertableToPdfA = self.disposedPureComputed(function() {
      return self.authModel.ok("convert-to-pdfa");
    });

    self.showArchivalError = self.disposedPureComputed(function() {
      return !self.archivable() && !self.archived() && !_.isEmpty(self.archivabilityError());
    });

    self.retentionDescription = self.disposedPureComputed(function() {
      var retention = util.getIn(self.metadata, ["sailytysaika"], {});
      if (ko.unwrap(retention.arkistointi)) {
        var retentionMode = ko.unwrap(retention.arkistointi);
        var additionalDetail = "";
        switch(retentionMode) {
        case "toistaiseksi":
          var laskentaperuste = ko.unwrap(retention.laskentaperuste);
          if (laskentaperuste) {
            additionalDetail = ", " + loc("laskentaperuste") + " " + loc(laskentaperuste);
          }
          break;
        case "m\u00E4\u00E4r\u00E4ajan":
          additionalDetail = ", " + ko.unwrap(retention.pituus) + " " + loc("vuotta");
          break;
        }
        return loc(retentionMode) + additionalDetail.toLowerCase();
      } else {
        return loc("<Arvo puuttuu>");
      }
    });

    self.personalDataDescription = self.disposedPureComputed(function() {
      return loc(util.getIn(self.metadata, ["henkilotiedot"]) || "<Arvo puuttuu>");
    });

    function getState(doc, metakey) {
      return util.getIn(doc, [metakey || "metadata", "tila"]) || "valmis";
    }

    if (doc.documentType === "application") {
      self.state = self.disposedPureComputed(_.partial( getState, application ));
    } else if (doc.documentType === "case-file") {
      self.state = self.disposedPureComputed(_.partial( getState, application, "processMetadata" ));
    } else {
      self.state = self.disposedPureComputed(_.partial( getState, doc ));
    }

    if (self.state() === "arkistoitu") {
      self.archived(true);
    }

    self.reset = function(doc) {
      self.metadata(ko.unwrap(doc.metadata));
      self.attachmentType(ko.unwrap(doc.typeString));

      var latestVersion = doc.latestVersion;
      self.archivable(Boolean( util.getIn(latestVersion, ["archivable"]) ));
      self.archivabilityError(util.getIn(latestVersion, ["archivabilityError"]));

    };
  }

  var initializedDocuments = {};

  function resetDocument(application, document) {
    // Document can be application doc, case-file doc or attachment.
    var id = util.getIn(document, ["id"]);
    var archivalDocumentModel = initializedDocuments[id] || new ArchivalSummaryDocumentModel(application, document());
    archivalDocumentModel.reset(document());
    initializedDocuments[id] = archivalDocumentModel;
    return archivalDocumentModel;
  }

  function selectIfArchivable(attachment) {
    var tila = util.getIn(attachment, ["metadata", "tila"]);
    if (attachment.archivable() && tila !== "arkistoitu") {
      attachment.sendToArchive(!attachment.sendToArchive());
    }
  }

  function initApplicationDocs(application) {
    if (!application.isArchivingProject()) {
      // Initialize archived non-attachment documents for application.
      var appDocId = application.id() + "-application";
      var caseFileDocId = application.id() + "-case-file";
      var docs = [ko.observable({
        documentNameKey: "applications.application",
        metadata: application.metadata,
        id: appDocId,
        previewAction: "pdf-export",
        documentType: "application"
      })];

      if (lupapisteApp.models.applicationAuthModel.ok("application-in-final-archiving-state")) {
        docs.push(ko.observable({
          documentNameKey: "caseFile.heading",
          metadata: application.processMetadata,
          id: caseFileDocId,
          previewAction: "pdfa-casefile",
          documentType: "case-file"
        }));
      }
      return docs;
    } else {
      return [];
    }
  }

  function ArchivalSummaryModel(params) {
    var self = this;

    ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

    self.attachments = self.disposedPureComputed(function() {
      return _.map(attachmentsService.attachments(), _.partial(resetDocument, params.application));
    });

    var applicationDocs = initApplicationDocs(params.application);

    var mainDocuments = self.disposedPureComputed(function() {
      return _.map(applicationDocs, _.partial(resetDocument, params.application));
    });

    var preAttachments = self.disposedPureComputed(function() {
      return attachmentsService.applyFilters(self.attachments(), [["preVerdict"], ["hasFile"]]);
    });
    var postAttachments = self.disposedPureComputed(function() {
      return attachmentsService.applyFilters(self.attachments(), [["postVerdict", "ram"], ["hasFile"]]);
    });

    var archivedPreAttachments = self.disposedPureComputed(function() {
      return _.filter(preAttachments(), isArchived);
    });
    var archivedPostAttachments = self.disposedPureComputed(function() {
      return _.filter(postAttachments(), isArchived);
    });

    if(params.application.isArchivingProject()) {
      self.archivedGroups = self.disposedPureComputed(function() {
        var atts = attachmentsService.applyFilters(self.attachments(), [["hasFile"]]);
        return getGroupList("archived-pre", _.filter(atts, isArchived));
      });
      self.notArchivedGroups = self.disposedPureComputed(function() {
        var atts = attachmentsService.applyFilters(self.attachments(), [["hasFile"]]);
        return getGroupList("not-archived-pre", _.reject(atts, isArchived));
      });
      self.archivedPostGroups = ko.observable();
      self.notArchivedPostGroups = ko.observable();
    } else {
      self.archivedGroups = self.disposedPureComputed(function () {
        return getGroupList("archived-pre", archivedPreAttachments());
      });
      self.archivedPostGroups = self.disposedPureComputed(function () {
        return getGroupList("archived-post", archivedPostAttachments());
      });
      self.notArchivedGroups = self.disposedPureComputed(function () {
        return getGroupList("not-archived-pre", _.reject(preAttachments(), isArchived));
      });
      self.notArchivedPostGroups = self.disposedPureComputed(function () {
        return getGroupList("not-archived-post", _.reject(postAttachments(), isArchived));
      });
    }

    self.archivedDocuments = self.disposedPureComputed(function() {
      return _.filter(mainDocuments(), isArchived);
    });
    self.notArchivedDocuments = self.disposedPureComputed(function() {
      return _.reject(mainDocuments(), isArchived);
    });

    self.showArchived = self.disposedPureComputed(function() {
      return !_.isEmpty(self.archivedDocuments()) || !_.isEmpty(self.archivedGroups()) || !_.isEmpty(self.archivedPostGroups());
    });
    self.showNotArchived = self.disposedPureComputed(function() {
      return !_.isEmpty(self.notArchivedDocuments()) || !_.isEmpty(self.notArchivedGroups()) || !_.isEmpty(self.notArchivedPostGroups());
    });

    var isSelectedForArchive = function(attachment) {
      return ko.unwrap(attachment.sendToArchive);
    };

    self.archivingInProgressIds = ko.observableArray();

    self.canArchive = self.disposedPureComputed( function() {
      return lupapisteApp.models.applicationAuthModel.ok("archive-documents");
    });

    self.archiveButtonEnabled = self.disposedPureComputed(function() {
      return self.canArchive()
        &&  _.isEmpty(self.archivingInProgressIds())
        && (_.some(preAttachments(), isSelectedForArchive)
            || _.some(postAttachments(), isSelectedForArchive)
            || _.some(mainDocuments(), isSelectedForArchive));
    });

    self.tosFunctionExists = self.disposedPureComputed(function() {
      return _.some(params.application.tosFunction());
    });

    self.archivingTimestampField = self.disposedPureComputed(function() {
      return params.application.submitted !== null ? loc("submission-date") : loc("creation-date");
    });

    function selectDocuments() {
      _.forEach(self.archivedDocuments(), function(doc) {
        var tila = util.getIn(doc, ["metadata", "tila"]);
        if (tila !== "arkistoitu") {
          doc.sendToArchive(!doc.sendToArchive());
        }
      });
    }

    self.selectAll = function() {
      _.forEach(archivedPreAttachments(), selectIfArchivable);
      _.forEach(archivedPostAttachments(), selectIfArchivable);
      selectDocuments();
    };

    self.unselectAll = function() {
      _.forEach(archivedPreAttachments(), function(attachment) {
        attachment.sendToArchive(false);
      });
      _.forEach(archivedPostAttachments(), function(attachment) {
        attachment.sendToArchive(false);
      });
      _.forEach(self.archivedDocuments(), function(doc) {
        doc.sendToArchive(false);
      });
    };

    self.selectAllPreAttachments = function() {
      _.forEach(archivedPreAttachments(), selectIfArchivable);
      selectDocuments();
    };

    self.selectAllPostAttachments = function() {
      _.forEach(archivedPostAttachments(), selectIfArchivable);
      selectDocuments();
    };

    var updateState = function(docs, newStateMap) {
      _.forEach(docs, function(doc) {
        var id = ko.unwrap(doc.id);
        if (_.has(newStateMap, id)) {
          var newState = newStateMap[id];
          var metadata = ko.unwrap(doc.metadata);
          if (metadata && _.isFunction(metadata.tila)) {
            metadata.tila(newState);
          } else if (metadata) {
            metadata.tila = ko.observable(newState);
          }
          if (newState === "arkistoitu") {
            doc.archived(true);
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

    var pollChangedState = function() {
      ajax
        .query("document-states",
          {
            id: ko.unwrap(params.application.id)
          })
        .success(function(data) {
          updateState(mainDocuments(), data.state);
          updateState(archivedPreAttachments(), data.state);
          updateState(archivedPostAttachments(), data.state);
        })
        .call();
    };

    var pollArchiveStatus = function() {
      pollChangedState();
      if (!_.isEmpty(self.archivingInProgressIds())) {
        window.setTimeout(pollArchiveStatus, 2000);
      } else {
        repository.load(ko.unwrap(params.application.id));
      }
    };

    if (ko.unwrap(params.application.id)) {
      pollChangedState();
    }

    self.archiveSelected = function() {
      var attachmentIds = _.map(_.filter(self.attachments(), isSelectedForArchive), function(attachment) {
        return ko.unwrap(attachment.id);
      });
      var mainDocumentIds = _.map(_.filter(mainDocuments(), isSelectedForArchive), function(doc) {
        return ko.unwrap(doc.id);
      });
      self.archivingInProgressIds(attachmentIds.concat(mainDocumentIds));
      ajax
        .command("archive-documents",
          {
            id: ko.unwrap(params.application.id),
            attachmentIds: attachmentIds,
            documentIds: mainDocumentIds
          })
        .success(function() {
          window.setTimeout(pollArchiveStatus, 3000);
        })
        .error(function(e) {
          window.setTimeout(pollArchiveStatus, 3000);
          error(e.text);
          notify.error(loc(e.text));
        })
        .call();
    };

    self.newTosFunction = ko.observable(ko.unwrap(params.application.tosFunction));
    self.tosFunctionCorrectionReason = ko.observable();
    self.tosFunctionCorrectionEnabled = self.disposedPureComputed(function() {
      return lupapisteApp.models.applicationAuthModel.ok("force-fix-tos-function-for-application") &&
        self.newTosFunction() !== params.application.tosFunction() &&
        self.tosFunctionCorrectionReason();
    });
    self.updateTosFunction = function() {
      var data = {
        id: ko.unwrap(params.application.id),
        functionCode: self.newTosFunction(),
        reason: self.tosFunctionCorrectionReason()
      };
      ajax
        .command("force-fix-tos-function-for-application", data)
        .success(function() {
          LUPAPISTE.ModalDialog.showDynamicOk(loc("application.tosMetadataWasResetTitle"), loc("application.tosMetadataWasReset"));
          self.tosFunctionCorrectionReason(null);
          repository.load(ko.unwrap(params.application.id), null, function(newApplication) {
            // Update application document. See initApplicationDocs for additional details.
            if (util.getIn(applicationDocs, [0, "metadata"])) {
              applicationDocs[0]().metadata(newApplication.metadata);
            }
            // Update case-file document. See initApplicationDocs for additional details.
            if (util.getIn(applicationDocs, [1, "metadata"])) {
              applicationDocs[1]().metadata(newApplication.processMetadata);
            }
          });
        })
        .call();
    };

    self.convertToPdfA = function(attachment) {
      var attachmentId = ko.unwrap(attachment.id);
      attachment.showAdditionalControls(true);
      attachmentsService.convertToPdfA(attachmentId);
    };

    self.archivingDates = ko.mapping.fromJS(_.defaults(params.application.archived,
      {"initial": null}, {"application": null}, {"completed": null}));

    self.blockMarkArchived = ko.observable(false);

    self.markPreVerdictPhaseArchivedEnabled = self.disposedPureComputed(function() {
      return !self.blockMarkArchived() && lupapisteApp.models.applicationAuthModel.ok("mark-pre-verdict-phase-archived");
    });

    self.markFullyArchivedEnabled = self.disposedPureComputed(function() {
      return !self.blockMarkArchived() && lupapisteApp.models.applicationAuthModel.ok("mark-fully-archived");
    });

    self.showMarkArchivedSection = self.disposedPureComputed(function() {
      return self.tosFunctionExists() &&
        (self.markPreVerdictPhaseArchivedEnabled() || self.markFullyArchivedEnabled()) &&
        !lupapisteApp.models.applicationAuthModel.ok("common-area-application");
    });

    function markApplicationArchived(cmd) {
      self.blockMarkArchived(true);
      ajax
        .command(cmd, {id: ko.unwrap(params.application.id)})
        .success(function() {
          window.setTimeout(function(){
            self.blockMarkArchived(false);
          }, 5000);
          repository.load(ko.unwrap(params.application.id));
        })
        .call();
    }

    self.markPreVerdictPhaseArchived = _.partial(markApplicationArchived, "mark-pre-verdict-phase-archived");

    self.markFullyArchived = _.partial(markApplicationArchived, "mark-fully-archived");

    var baseModelDispose = self.dispose;
    self.dispose = function() {
      _.invokeMap(initializedDocuments, "dispose");
      _.forEach(groupLists, function(gl) { _.invokeMap(gl.initializedGroups, "dispose"); });
      initializedDocuments = {};
      groupLists = {};
      baseModelDispose();
    };
  }

  ko.components.register("archival-summary", {
    viewModel: ArchivalSummaryModel,
    template: {element: "archival-summary-template"}
  });

})();
