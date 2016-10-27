(function() {
  "use strict";

  var attachmentsService = lupapisteApp.services.attachmentsService;

  function GroupModel(groupName, groupDesc, attachments, building) {
    var self = this;
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
    self.name = ko.computed( function() {
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

  function getGroupList(attachments, buildings) {
    if (_.isEmpty(attachments)) {
      return [];
    }

    var grouped = _.groupBy(attachments, function(attachment) {
      return util.getIn(attachment, ["op", "id"]) || generalAttachmentsStr;
    });

    var mapped = _.map(grouped, function(attachments, group) {
      if (group === generalAttachmentsStr) {
        return new GroupModel(group, null, attachments);
      } else {
        var attachment = _.head(attachments);
        var operation  = ko.unwrap(attachment.op);
        return new GroupModel(operation.name, operation.description, attachments, buildings[operation.id]);
      }
    });

    return _.sortBy(mapped, function(group) { // attachments.general on top, else sort by op.created
      if ( group.groupName === generalAttachmentsStr ) {
        return -1;
      } else {
        return util.getIn(group.attachments, [0, "op", "created"]);
      }
    });

  }

  function isConvertableType(contentType) {
    return (LUPAPISTE.config.convertableTypes.indexOf(contentType) !== -1);
  }

  function ArchivalSummaryDocumentModel(application, doc) {
    var self = _.assign(this, doc);

    ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

    self.authModel = attachmentsService.authModels()[doc.id] || lupapisteApp.models.applicationAuthModel;

    self.metadata = ko.observable(ko.unwrap(doc.metadata));

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

    self.convertableToPdfA = ko.observable();

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

    self.reset = function(doc) {
      self.metadata(ko.unwrap(doc.metadata));
      self.attachmentType = ko.observable(ko.unwrap(doc.typeString));
      //self.showAdditionalControls(false);

      var latestVersion = doc.latestVersion;
      self.archivable(Boolean( util.getIn(latestVersion, ["archivable"]) ));
      self.archivabilityError(util.getIn(latestVersion, ["archivabilityError"]));

      if (latestVersion && _.isUndefined(latestVersion.archivable) && isConvertableType(ko.unwrap(latestVersion.contentType))) {
        self.convertableToPdfA(true);
      } else {
        self.convertableToPdfA(_.has(latestVersion, "archivable") ? !ko.unwrap(latestVersion.archivable) : false);
      }
    };
  }

  var initializedDocuments = {};

  function resetDocument(application, document) {
    var id = util.getIn(document, ["id"]);
    var archivalDocumentModel = initializedDocuments[id] || new ArchivalSummaryDocumentModel(application, ko.unwrap(document));
    archivalDocumentModel.reset(ko.unwrap(document));
    initializedDocuments[id] = archivalDocumentModel;
    return archivalDocumentModel;
  }

  function selectIfArchivable(attachment) {
    var tila = util.getIn(attachment, ["metadata", "tila"]);
    if (attachment.archivable && tila !== "arkistoitu") {
      attachment.sendToArchive(true);
    }
  }

  function filteredDocs(params) {
    var applicationState = params.application.state();
    var appDocId = params.application.id() + "-application";
    var caseFileDocId = params.application.id() + "-case-file";
    var docs = [
      {documentNameKey: "applications.application", metadata: params.application.metadata, id: appDocId, previewAction: "pdf-export", documentType: "application"}
    ];

    if (applicationState === "extinct" || applicationState === "closed") {
      docs.push({documentNameKey: "caseFile.heading", metadata: params.application.processMetadata, id: caseFileDocId, previewAction: "pdfa-casefile", documentType: "case-file"});
    }
    return docs;
  }

  var model = function(params) {
    var self = this;
    self.attachments = ko.pureComputed(function() {
      return _.map(attachmentsService.attachments(), _.partial(resetDocument, params.application));
    });

    var docs = filteredDocs(params);

    var mainDocuments = ko.pureComputed(function() {
      return _.map(filteredDocs(params), _.partial(resetDocument, params.application));
    });

    var preAttachments = ko.pureComputed(function() {
      return attachmentsService.applyFilters(self.attachments(), [["preVerdict"], ["hasFile"]]);
    });
    var postAttachments = ko.pureComputed(function() {
      return attachmentsService.applyFilters(self.attachments(), [["postVerdict"], ["hasFile"]]);
    });

    var archivedPreAttachments = ko.pureComputed(function() {
      return _.filter(preAttachments(), isArchived);
    });
    var archivedPostAttachments = ko.pureComputed(function() {
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

    self.archivedGroups = ko.pureComputed(function() {
      return getGroupList(archivedPreAttachments(), buildings);
    });
    self.archivedPostGroups = ko.pureComputed(function() {
      return getGroupList(archivedPostAttachments(), buildings);
    });
    self.notArchivedGroups = ko.pureComputed(function() {
      return getGroupList(_.reject(preAttachments(), isArchived), buildings);
    });
    self.notArchivedPostGroups = ko.pureComputed(function() {
      return getGroupList(_.reject(postAttachments(), isArchived), buildings);
    });

    self.archivedDocuments = ko.pureComputed(function() {
      return _.filter(mainDocuments(), isArchived);
    });
    self.notArchivedDocuments = ko.pureComputed(function() {
      return _.reject(mainDocuments(), isArchived);
    });
    self.showArchived = ko.pureComputed(function() {
      return !_.isEmpty(self.archivedDocuments()) || !_.isEmpty(self.archivedGroups()) || !_.isEmpty(self.archivedPostGroups());
    });
    self.showNotArchived = ko.pureComputed(function() {
      return !_.isEmpty(self.notArchivedDocuments()) || !_.isEmpty(self.notArchivedGroups()) || !_.isEmpty(self.notArchivedPostGroups());
    });

    var attachmentGroupLabel = function(groupName) {
      return loc(["attachmentType", groupName, "_group_label"]);
    };

    var attachmentTypeLabel = function(groupName, typeName) {
      return loc(["attachmentType", groupName, typeName]);
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

    var allIds = _.map(self.attachments().concat(docs), getId);

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
    self.tosFunctionCorrectionEnabled = ko.pureComputed(function() {
      return self.newTosFunction() !== params.application.tosFunction() && self.tosFunctionCorrectionReason();
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
            // Update application document. See filteredDocs for additionanl details.
            if (util.getIn(docs, [0, "metadata"])) {
              docs[0].metadata(ko.mapping.fromJS(newApplication.metadata));
            }
            // Update case-file document. See filteredDocs for additionanl details.
            if (util.getIn(docs, [1, "metadata"])) {
              docs[1].metadata(ko.mapping.fromJS(newApplication.processMetadata));
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
  };

  ko.components.register("archival-summary", {
    viewModel: model,
    template: {element: "archival-summary-template"}
  });

})();
