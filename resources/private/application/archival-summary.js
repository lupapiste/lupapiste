(function() {
  "use strict";

  var attachmentsService = lupapisteApp.services.attachmentsService;

  function GroupModel(groupName, groupDesc, attachments, building) {
    var self = this;

    ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

    self.attachments = ko.observableArray(attachments);
    self.groupName = groupName;
    var fullGroupDesc = "";
    if (groupDesc) {
      fullGroupDesc += " - " + groupDesc;
    }
    if (building) {
      fullGroupDesc += " - " + building;
    }
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

  function getGroupList(groupListId, attachments, buildings) {
    // Group list for attachments grouped by archived/not-archived and pre-/postverdict
    if (_.isEmpty(attachments)) {
      return [];
    }

    function getGroup(groupList, attachments, group) {
      if (!groupList.initializedGroups[group]) {
        var op  = util.getIn(attachments, [0, "op"]);
        var groupModel = group === generalAttachmentsStr ? new GroupModel(group, null, attachments) : new GroupModel(op.name, op.description, attachments, buildings[op.id]);
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
        return util.getIn(attachment, ["op", "id"]) || generalAttachmentsStr;
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

    self.convertableToPdfA = self.disposedPureComputed(function() {
      return self.authModel.ok("convert-to-pdfa");
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
      attachment.sendToArchive(true);
    }
  }

  function initApplicationDocs(application) {
    // Initialize archived non-attachment documents for application.
    var applicationState = application.state();
    var appDocId = application.id() + "-application";
    var caseFileDocId = application.id() + "-case-file";
    var docs = [ ko.observable({ documentNameKey: "applications.application",
                                 metadata: application.metadata,
                                 id: appDocId,
                                 previewAction: "pdf-export",
                                 documentType: "application" }) ];

    if (["extinct", "closed", "foremanVerdictGiven"].indexOf(applicationState) !== -1) {
      docs.push( ko.observable({ documentNameKey: "caseFile.heading",
                                 metadata: application.processMetadata,
                                 id: caseFileDocId,
                                 previewAction: "pdfa-casefile",
                                 documentType: "case-file" }) );
    }
    return docs;
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
      return attachmentsService.applyFilters(self.attachments(), [["postVerdict"], ["hasFile"]]);
    });

    var archivedPreAttachments = self.disposedPureComputed(function() {
      return _.filter(preAttachments(), isArchived);
    });
    var archivedPostAttachments = self.disposedPureComputed(function() {
      return _.filter(postAttachments(), isArchived);
    });

    var buildings = _
      .chain(params.application._js.buildings)
      .filter(function(val) {
        return val.operationId && (val.nationalId || val.localId);
      })
      .keyBy("operationId")
      .mapValues(function(val) {
        return val.nationalId || val.localId;
      })
      .value();

    self.archivedGroups = self.disposedPureComputed(function() {
      return getGroupList("archived-pre", archivedPreAttachments(), buildings);
    });
    self.archivedPostGroups = self.disposedPureComputed(function() {
      return getGroupList("archived-post", archivedPostAttachments(), buildings);
    });
    self.notArchivedGroups = self.disposedPureComputed(function() {
      return getGroupList("not-archived-pre", _.reject(preAttachments(), isArchived), buildings);
    });
    self.notArchivedPostGroups = self.disposedPureComputed(function() {
      return getGroupList("not-archived-post", _.reject(postAttachments(), isArchived), buildings);
    });

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

    self.archiveButtonEnabled = self.disposedPureComputed(function() {
      return  lupapisteApp.models.applicationAuthModel.ok("archive-documents") &&
        _.isEmpty(self.archivingInProgressIds()) && (_.some(preAttachments(), isSelectedForArchive) ||
        _.some(postAttachments(), isSelectedForArchive) || _.some(mainDocuments(), isSelectedForArchive));
    });

    self.selectAll = function() {
      _.forEach(archivedPreAttachments(), selectIfArchivable);
      _.forEach(archivedPostAttachments(), selectIfArchivable);
      _.forEach(self.archivedDocuments(), function(doc) {
        var tila = util.getIn(doc, ["metadata", "tila"]);
        if (tila !== "arkistoitu") {
          doc.sendToArchive(true);
        }
      });
    };

    self.selectAllPreAttachments = function() {
      _.forEach(archivedPreAttachments(), selectIfArchivable);
    };

    self.selectAllPostAttachments = function() {
      _.forEach(archivedPostAttachments(), selectIfArchivable);
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

    var allIds = _.map(self.attachments().concat(mainDocuments()), getId);

    if (ko.unwrap(params.application.id)) {
      pollChangedState(allIds);
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

    self.showMarkArchivedSection = ko.observable(!params.application._js.archived.application);
    self.markApplicationArchived = function() {
      ajax
        .command("mark-pre-verdict-phase-archived", {id: ko.unwrap(params.application.id)})
        .success(function() {
          repository.load(ko.unwrap(params.application.id));
          self.showMarkArchivedSection(false);
        })
        .call();
    };

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
