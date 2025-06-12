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

  function formatVersion(version) {
    return version ? version.major + "." + version.minor : "";
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

    self.getId = function() {
      return self.id;
    };

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

    self.canBeArchived = ko.observable();
    self.archivabilityError = ko.observable();

    self.sendToArchive = ko.observable(false);
    self.archived = ko.observable(false);

    self.showConversionLog = ko.observable(false);
    self.convertableToPdfA = self.disposedPureComputed(function() {
      return self.authModel.ok("convert-to-pdfa");
    });

    self.showArchivalError = self.disposedPureComputed(function() {
      return !self.canBeArchived() && !self.archived() && !_.isEmpty(self.archivabilityError());
    });

    self.versionString = formatVersion(util.getIn(doc, ["latestVersion", "version"]));

    self.exportVersionString = self.disposedPureComputed(function() {
      var version = util.getIn(self.metadata, ["export-version"]);
      return version ? "1." + (version - 1) : "";
    });

    self.retentionDescription = self.disposedPureComputed(function() {
      var retention = util.getIn(self.metadata, ["sailytysaika"], {});
      var retentionMode = ko.unwrap(retention.arkistointi);
      if (retentionMode) {
        var additionalDetail = "";
        switch(retentionMode) {
        case "toistaiseksi":
          var laskentaperuste = ko.unwrap(retention.laskentaperuste);
          if (laskentaperuste) {
            additionalDetail = ", " + loc("laskentaperuste") + " " + loc(laskentaperuste);
          }
          break;
        case "määräajan":
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
      self.canBeArchived(Boolean( util.getIn(doc, ["can-be-archived?"])));
      self.archivabilityError(util.getIn(latestVersion, ["archivabilityError"]));

    };
  }

  function ArchivalSummaryBIMFile(application, bimModel) {
    var self = _.assign(this, bimModel);

    self.getId = function() {
      return util.getIn(bimModel, ["target-entity", "file-id"]);
    };

    ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

    self.authModel = lupapisteApp.models.applicationAuthModel;

    self.metadata = ko.observable(ko.unwrap(bimModel.metadata));
    self.attachmentType = ko.observable(util.getIn(bimModel, ["target-entity", "target-type"]));

    self.typeChange = util.getIn(bimModel, ["type", "type-group"]) && util.getIn(bimModel, ["type", "type-id"]) &&
      new LUPAPISTE.AttachmentsChangeTypeModel({ authModel: self.authModel,
                                                 allowedAttachmentTypes: lupapisteApp.models.application.allowedAttachmentTypes(),
                                                 attachmentType: self.type,
                                                 attachmentId: self.id });

    self.addEventListener("bim-attachments", {eventType: "bim-attachment-type-selected", attachmentId: self.getId()}, function(data) {
      self.type(data.attachmentType);
    });

    self.showAdditionalControls = ko.observable(false);
    self.toggleAdditionalControls = function() {
      self.showAdditionalControls(!self.showAdditionalControls());
    };

    self.canBeArchived = ko.observable(true);
    self.archivabilityError = ko.observable();

    self.sendToArchive = ko.observable(false);
    self.archived = ko.observable(false);

    self.showConversionLog = ko.observable(false);
    self.convertableToPdfA = ko.observable(false);

    self.showArchivalError = self.disposedPureComputed(function() {
      return !self.canBeArchived() && !self.archived() && !_.isEmpty(self.archivabilityError());
    });

    self.versionString = formatVersion(util.getIn(_.maxBy(bimModel.versions, "uploaded-at"), ["version"]));

    self.retentionDescription = self.disposedPureComputed(function() {
      var retention = util.getIn(self.metadata, ["sailytysaika"], {});
      var retentionMode = ko.unwrap(retention.arkistointi);
      if (retentionMode) {
        var additionalDetail = "";
        switch(retentionMode) {
        case "toistaiseksi":
          var laskentaperuste = ko.unwrap(retention.laskentaperuste);
          if (laskentaperuste) {
            additionalDetail = ", " + loc("laskentaperuste") + " " + loc(laskentaperuste);
          }
          break;
        case "määräajan":
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

    function getState(model, metakey) {
      return util.getIn(model, [metakey || "metadata", "tila"]) || "valmis";
    }

    if (bimModel.documentType === "application") {
      self.state = self.disposedPureComputed(_.partial( getState, application ));
    } else if (bimModel.documentType === "case-file") {
      self.state = self.disposedPureComputed(_.partial( getState, application, "processMetadata" ));
    } else {
      self.state = self.disposedPureComputed(_.partial( getState, bimModel ));
    }

    if (self.state() === "arkistoitu") {
      self.archived(true);
    }

    self.reset = function(bimModel) {
      self.metadata(ko.unwrap(bimModel.metadata));
      self.attachmentType(ko.unwrap(bimModel.typeString));

      var latestVersion = bimModel.latestVersion;
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

  function selectIfSendableToArchival(attachment, sftpExportMode) {
    var tila = util.getIn(attachment, ["metadata", "tila"]);
    if (sftpExportMode || (attachment.canBeArchived() && tila !== "arkistoitu")) {
      attachment.sendToArchive(true);
    }
  }

  function initApplicationDocs(application) {
    if (!application.isArchivingProject()) {
      // Initialize archived non-attachment documents for application.
      var appDocId = application.id() + "-application";
      var caseFileDocId = application.id() + "-case-file";
      var foremanListDocId = application.id() + "-foreman-list";
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

      if (lupapisteApp.models.applicationAuthModel.ok("application-has-foreman")) {
        docs.push(ko.observable({
          documentNameKey: "attachmentType.osapuolet.tyonjohtajaluettelo",
          metadata: application["foreman-list-metadata"],
          id: foremanListDocId,
          previewAction: "pdf-foreman-list",
          documentType: "osapuolet.tyonjohtajaluettelo"}));
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
      return _.map(attachmentsService.allAttachments(), _.partial(resetDocument, params.application));
    });

    self.bimModels = ko.observableArray();
    self.bimAttachments = ko.observableArray();

    var fetchLinkedFiles = function fetchLinkedFiles(application, targetType, observable) {
      ajax
        .query("fetch-linked-file-metadatas-for-application",
               {
                 id: ko.unwrap(application.id),
                 "target-type": targetType,
               })
        .success(function(response) {
          var archivalModels = _.map(response.data, function(bimModel) {
            return new ArchivalSummaryBIMFile(application, bimModel);
          });
          observable.push.apply(observable, archivalModels);
        })
        .call();
    };

    if(features.enabled("bim-archiving")) {
      fetchLinkedFiles(params.application, "bim-model-file", self.bimModels);
      fetchLinkedFiles(params.application, "bim-inspection-report", self.bimAttachments);
      fetchLinkedFiles(params.application, "bim-additional-file", self.bimAttachments);
    }

    var applicationDocs = initApplicationDocs(params.application);

    self.secretBuildings = self.disposedPureComputed(function() {
      var isSecret = function (b) {return b.secret();};
      var getVtjprt = function (b) {return b["vtj-prt"]();};
      var buildings = _.filter(params.application["document-buildings"](), isSecret);
      if (buildings.length === 0) {
        return null;
      }
      var vtjprts = _.map(buildings, getVtjprt);
      return loc("archivalSummary.secretBuildings", vtjprts.join(", "));
    });

    var bimModelFileIDModelPhaseMap = function() {
      return self.bimModels().reduce(function(acc, model) {
        var fileID = util.getIn(model, ["target-entity", "file-id"]);
        var phase = util.getIn(model, ["metadata", "model-phase"]);
        acc[fileID] = phase;
        return acc;
      }, {});
    };

    var filterModelsByPhase = function(phaseName, models) {
      return _.filter(models, function(model) {
        var phase = util.getIn(model, ["metadata", "model-phase"]);
        return phase === phaseName;
      });
    };

    var filterModelAttachmentsByPhase = function(phaseName, modelAttachments) {
      var modelPhases = bimModelFileIDModelPhaseMap();
      return _.filter(modelAttachments, function(bimAttachment) {
        var modelFileID = util.getIn(bimAttachment, ["target-entity", "parent-file-id"]);
        return modelPhases[modelFileID] === phaseName;
      });
    };

    self.submissionTimeBIMModels = self.disposedPureComputed(function() {
      return filterModelsByPhase("submission-time", self.bimModels());
    });
    self.submissionTimeBIMModelAttachments = self.disposedPureComputed(function() {
      return filterModelAttachmentsByPhase("submission-time", self.bimAttachments());
    });

    self.archivedSubmissionTimeBIMModels = self.disposedPureComputed(function() {
       return _.filter(self.submissionTimeBIMModels(), isArchived);
    });

    self.archivedSubmissionTimeBIMModelAttachments = self.disposedPureComputed(function() {
       return _.filter(self.submissionTimeBIMModelAttachments(), isArchived);
    });

    self.notArchivedSubmissionTimeBIMModels = self.disposedPureComputed(function() {
       return _.reject(self.submissionTimeBIMModels(), isArchived);
    });

    self.notArchivedSubmissionTimeBIMModelAttachments = self.disposedPureComputed(function() {
       return _.reject(self.submissionTimeBIMModelAttachments(), isArchived);
    });

    self.constructionTimeBIMModels = self.disposedPureComputed(function() {
      return filterModelsByPhase("construction-time", self.bimModels());
    });
    self.constructionTimeBIMModelAttachments = self.disposedPureComputed(function() {
      return filterModelAttachmentsByPhase("construction-time", self.bimAttachments());
    });

    self.archivedConstructionTimeBIMModels = self.disposedPureComputed(function() {
       return _.filter(self.constructionTimeBIMModels(), isArchived);
    });

    self.archivedConstructionTimeBIMModelAttachments = self.disposedPureComputed(function() {
       return _.filter(self.constructionTimeBIMModelAttachments(), isArchived);
    });

    self.notArchivedConstructionTimeBIMModels = self.disposedPureComputed(function() {
       return _.reject(self.constructionTimeBIMModels(), isArchived);
    });

    self.notArchivedConstructionTimeBIMModelAttachments = self.disposedPureComputed(function() {
       return _.reject(self.constructionTimeBIMModelAttachments(), isArchived);
    });

    self.completionTimeBIMModels = self.disposedPureComputed(function() {
      return filterModelsByPhase("completion-time", self.bimModels());
    });

    self.completionTimeBIMModelAttachments = self.disposedPureComputed(function() {
      return filterModelAttachmentsByPhase("completion-time", self.bimAttachments());
    });

    self.archivedCompletionTimeBIMModels = self.disposedPureComputed(function() {
       return _.filter(self.completionTimeBIMModels(), isArchived);
    });

    self.archivedCompletionTimeBIMModelAttachments = self.disposedPureComputed(function() {
       return _.filter(self.completionTimeBIMModelAttachments(), isArchived);
    });

    self.notArchivedCompletionTimeBIMModels = self.disposedPureComputed(function() {
       return _.reject(self.completionTimeBIMModels(), isArchived);
    });

    self.notArchivedCompletionTimeBIMModelAttachments = self.disposedPureComputed(function() {
       return _.reject(self.completionTimeBIMModelAttachments(), isArchived);
    });

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

    var notArchivedPreAttachments = self.disposedPureComputed(function() {
      return _.reject(preAttachments(), isArchived);
    });
    var notArchivedPostAttachments = self.disposedPureComputed(function() {
      return _.reject(postAttachments(), isArchived);
    });

    self.archivedCompletionTimeAttachments = self.disposedPureComputed(function() {
      return [];
    });
    self.notArchivedCompletionTimeAttachments = self.disposedPureComputed(function() {
      return [];
    });

    var archivedCollections = [archivedPreAttachments, archivedPostAttachments,
      self.archivedSubmissionTimeBIMModels, self.archivedSubmissionTimeBIMModelAttachments,
      self.archivedConstructionTimeBIMModels, self.archivedConstructionTimeBIMModelAttachments,
      self.archivedCompletionTimeBIMModels, self.archivedCompletionTimeBIMModelAttachments];

    var notArchivedCollections = [notArchivedPreAttachments, notArchivedPostAttachments,
      self.notArchivedSubmissionTimeBIMModels, self.notArchivedSubmissionTimeBIMModelAttachments,
      self.notArchivedConstructionTimeBIMModels, self.notArchivedConstructionTimeBIMModelAttachments,
      self.notArchivedCompletionTimeBIMModels, self.notArchivedCompletionTimeBIMModelAttachments];

    var archivedPreCollections = [archivedPreAttachments,
      self.archivedSubmissionTimeBIMModels, self.archivedSubmissionTimeBIMModelAttachments];

    var archivedPostCollections = [archivedPostAttachments,
      self.archivedConstructionTimeBIMModels, self.archivedConstructionTimeBIMModelAttachments,
      self.archivedCompletionTimeBIMModels, self.archivedCompletionTimeBIMModelAttachments];

    var notArchivedPreCollections = [notArchivedPreAttachments,
      self.notArchivedSubmissionTimeBIMModels, self.notArchivedSubmissionTimeBIMModelAttachments];

    var notArchivedPostCollections = [notArchivedPostAttachments,
      self.notArchivedConstructionTimeBIMModels, self.notArchivedConstructionTimeBIMModelAttachments,
      self.notArchivedCompletionTimeBIMModels, self.notArchivedCompletionTimeBIMModelAttachments];

    function forEachInCollections(collections, fn) {
      _.forEach(collections, function(coll) { _.forEach(ko.unwrap(coll), fn); });
    }

    function someInCollections(collections, fn) {
      return _.some(collections, function(coll) { return _.some(ko.unwrap(coll), fn); });
    }

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
      return !_.isEmpty(self.archivedDocuments())
        || !_.isEmpty(self.archivedGroups())
        || !_.isEmpty(self.archivedPostGroups())
        || !_.isEmpty(self.archivedSubmissionTimeBIMModels())
        || !_.isEmpty(self.archivedSubmissionTimeBIMModelAttachments())
        || !_.isEmpty(self.archivedConstructionTimeBIMModels())
        || !_.isEmpty(self.archivedConstructionTimeBIMModelAttachments())
        || !_.isEmpty(self.archivedCompletionTimeBIMModels())
        || !_.isEmpty(self.archivedCompletionTimeBIMModelAttachments());
    });
    self.showNotArchived = self.disposedPureComputed(function() {
      return !_.isEmpty(self.notArchivedDocuments())
        || !_.isEmpty(self.notArchivedGroups())
        || !_.isEmpty(self.notArchivedPostGroups())
        || !_.isEmpty(self.notArchivedSubmissionTimeBIMModels())
        || !_.isEmpty(self.notArchivedSubmissionTimeBIMModelAttachments())
        || !_.isEmpty(self.notArchivedConstructionTimeBIMModels())
        || !_.isEmpty(self.notArchivedConstructionTimeBIMModelAttachments())
        || !_.isEmpty(self.notArchivedCompletionTimeBIMModels())
        || !_.isEmpty(self.notArchivedCompletionTimeBIMModelAttachments());
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
            || _.some(mainDocuments(), isSelectedForArchive)
            || _.some(self.bimModels(), isSelectedForArchive)
            || _.some(self.bimAttachments(), isSelectedForArchive)
           );
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
          doc.sendToArchive(true);
        }
      });
    }

    self.selectAll = function() {
      var sftpMode = self.sftpExportMode();
      forEachInCollections(archivedCollections, function(att) { selectIfSendableToArchival(att, sftpMode); });
      selectDocuments();
    };

    self.unselectAll = function() {
      var unselectFn = function(item) { item.sendToArchive(false); };
      forEachInCollections(archivedCollections, unselectFn);
      _.forEach(self.archivedDocuments(), unselectFn);
    };

    self.selectAllPreAttachments = function() {
      var sftpMode = self.sftpExportMode();
      forEachInCollections(archivedPreCollections, function(att) { selectIfSendableToArchival(att, sftpMode); });
      selectDocuments();
    };

    self.selectAllPostAttachments = function() {
      var sftpMode = self.sftpExportMode();
      forEachInCollections(archivedPostCollections, function(att) { selectIfSendableToArchival(att, sftpMode); });
      selectDocuments();
    };

    // Not-archived section selection functions (SFTP export mode)

    function selectNotArchivedDocuments() {
      _.forEach(self.notArchivedDocuments(), function(doc) {
        doc.sendToArchive(true);
      });
    }

    self.selectAllNotArchived = function() {
      var sftpMode = self.sftpExportMode();
      forEachInCollections(notArchivedCollections, function(att) { selectIfSendableToArchival(att, sftpMode); });
      selectNotArchivedDocuments();
    };

    self.unselectAllNotArchived = function() {
      forEachInCollections(notArchivedCollections, function(item) { item.sendToArchive(false); });
      _.forEach(self.notArchivedDocuments(), function(item) { item.sendToArchive(false); });
    };

    self.selectAllNotArchivedPre = function() {
      var sftpMode = self.sftpExportMode();
      forEachInCollections(notArchivedPreCollections, function(att) { selectIfSendableToArchival(att, sftpMode); });
      selectNotArchivedDocuments();
    };

    self.selectAllNotArchivedPost = function() {
      var sftpMode = self.sftpExportMode();
      forEachInCollections(notArchivedPostCollections, function(att) { selectIfSendableToArchival(att, sftpMode); });
      selectNotArchivedDocuments();
    };

    var isSelected = function(item) { return ko.unwrap(item.sendToArchive); };

    self.anyArchivedSelected = self.disposedPureComputed(function() {
      return someInCollections(archivedCollections, isSelected)
        || _.some(self.archivedDocuments(), isSelected);
    });

    self.anyNotArchivedSelected = self.disposedPureComputed(function() {
      return someInCollections(notArchivedCollections, isSelected)
        || _.some(self.notArchivedDocuments(), isSelected);
    });

    var updateState = function(docs, newStateMap) {
      _.forEach(docs, function(doc) {
        var id = doc.getId();
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
          updateState(self.bimModels(), data["linked-file-state"]);
          updateState(self.bimAttachments(), data["linked-file-state"]);
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
      var linkedBIMFileIds = _.map(_.filter(self.bimModels(), isSelectedForArchive), function(model) {
        return ko.unwrap(model.getId());
      });
      var linkedBIMAttachmentIds = _.map(_.filter(self.bimAttachments(), isSelectedForArchive), function(attachment) {
        return ko.unwrap(attachment.getId());
      });
      self.archivingInProgressIds(attachmentIds.concat(mainDocumentIds).concat(linkedBIMFileIds).concat(linkedBIMAttachmentIds));
      ajax
        .command("archive-documents",
          {
            id: ko.unwrap(params.application.id),
            attachmentIds: attachmentIds,
            documentIds: mainDocumentIds,
            linkedFileIds: linkedBIMFileIds.concat(linkedBIMAttachmentIds)
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

    // SFTP Export mode (replaces archiving for some organizations)
    self.sftpExportMode = self.disposedPureComputed(function() {
      return lupapisteApp.models.applicationAuthModel.ok("sftp-export-enabled");
    });

    self.archiveColumnHeader = self.disposedPureComputed(function() {
      return self.sftpExportMode() ? loc("attachment.th-transfer") : "A";
    });

    self.archiveToggleLtext = self.disposedPureComputed(function() {
      return self.sftpExportMode() ? "attachment.th-transfer" : "arkistoidaan";
    });

    self.exportInProgress = ko.observable(false);

    self.exportButtonEnabled = self.disposedPureComputed(function() {
      return self.sftpExportMode()
        && !self.exportInProgress()
        && (_.some(preAttachments(), isSelectedForArchive)
            || _.some(postAttachments(), isSelectedForArchive)
            || _.some(mainDocuments(), isSelectedForArchive)
            || _.some(self.bimModels(), isSelectedForArchive)
            || _.some(self.bimAttachments(), isSelectedForArchive));
    });

    self.exportSelected = function() {
      var attachmentIds = _.map(_.filter(self.attachments(), isSelectedForArchive), function(attachment) {
        return ko.unwrap(attachment.id);
      });
      var linkedBIMFileIds = _.map(_.filter(self.bimModels(), isSelectedForArchive), function(model) {
        return ko.unwrap(model.getId());
      });
      var linkedBIMAttachmentIds = _.map(_.filter(self.bimAttachments(), isSelectedForArchive), function(attachment) {
        return ko.unwrap(attachment.getId());
      });
      var documentIds = _.map(_.filter(mainDocuments(), isSelectedForArchive), function(doc) {
        return ko.unwrap(doc.id);
      });

      self.exportInProgress(true);
      ajax
        .command("move-attachments-to-backing-system",
          {
            id: ko.unwrap(params.application.id),
            lang: loc.getCurrentLanguage(),
            attachmentIds: attachmentIds.concat(linkedBIMFileIds).concat(linkedBIMAttachmentIds),
            documentIds: documentIds
          })
        .success(function(resp) {
          self.exportInProgress(false);
          var failedIds = resp["documents-failed"];
          if (!_.isEmpty(failedIds)) {
            var failedNames = _.map(failedIds, function(docId) {
              var doc = _.find(mainDocuments(), function(d) { return ko.unwrap(d.id) === docId; });
              return doc ? loc(ko.unwrap(doc.documentNameKey)) : docId;
            });
            notify.error(loc("archivalSummary.exportPartialSuccess", failedNames.join(", ")));
          } else {
            notify.success(loc("archivalSummary.exportSuccess"));
          }
          pollChangedState();
          repository.load(ko.unwrap(params.application.id));
        })
        .error(function(e) {
          self.exportInProgress(false);
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

    self.nonEmptyTable = function(attachments, bimModels, bimAttachments) {
      return !(_.isEmpty(attachments()) && _.isEmpty(bimModels()) && _.isEmpty(bimAttachments()));
    };

    self.getAttachmentCountForModel = function(modelFileId, attachments) {
      return _.filter(ko.unwrap(attachments), function(att) {
        return util.getIn(att, ["target-entity", "parent-file-id"]) === modelFileId;
      }).length;
    };

    self.getParentModelForAttachment = function(attachment) {
      var parentFileId = util.getIn(attachment, ["target-entity", "parent-file-id"]);
      return _.find(self.bimModels(), function(model) {
        return model.getId() === parentFileId;
      });
    };

    self.getParentModelName = function(attachment) {
      var parentModel = self.getParentModelForAttachment(attachment);
      return parentModel ? util.getIn(parentModel, ["metadata", "name"]) : "";
    };

    self.isParentModelExportable = function(attachment) {
      var parentModel = self.getParentModelForAttachment(attachment);
      if (!parentModel) {
        return false;
      }
      // Parent is exportable if it's already archived or selected for export
      return ko.unwrap(parentModel.archived) || ko.unwrap(parentModel.sendToArchive);
    };

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
