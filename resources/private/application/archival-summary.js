(function() {
  "use strict";

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

  var getGroupList = function(attachments, buildings) {
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
        return new GroupModel(ko.unwrap(att.op.name), ko.unwrap(att.op.description), attachments, buildings[att.op.id()]);
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

  var openAttachmentIds = [];

  var addAdditionalFieldsToAttachments = function(attachments, applicationId) {
    return _.map(attachments, function(attachment) {
      if (!_.isFunction(attachment.metadata)) {
        attachment.metadata = ko.observable(attachment.metadata);
      }
      if (attachment.id) {
        var idIndex = openAttachmentIds.indexOf(ko.unwrap(attachment.id));
        attachment.showAdditionalControls = ko.observable(idIndex !== -1);
        if (idIndex > -1) {
          openAttachmentIds.splice(idIndex, 1);
        }
      } else {
        attachment.showAdditionalControls = ko.observable(false);
      }
      attachment.retentionDescription = ko.pureComputed(function() {
        var retention = attachment.metadata() ? attachment.metadata().sailytysaika : null;
        if (retention && retention.arkistointi()) {
          var retentionMode = retention.arkistointi();
          var additionalDetail = "";
          switch(retentionMode) {
            case "toistaiseksi":
              var laskentaperuste = util.getIn(retention, ["laskentaperuste"]);
              if (laskentaperuste) {
                additionalDetail = ", " + loc("laskentaperuste") + " " + loc(laskentaperuste);
              }
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
      if (attachment.contents) {
        attachment.contents.subscribe(function (value) {
          ajax
            .command("set-attachment-contents",
            {id: applicationId, attachmentId: attachment.id(), contents: value})
            .success(function() {
              hub.send("indicator-icon", {style: "positive"});
              repository.load(applicationId);
            })
            .error(function(e) {
              repository.load(applicationId);
              error(e.text);
            })
            .call();
        });
      }
      var lv = attachment.latestVersion;
      attachment.archivable = lv && _.isFunction(lv.archivable) ? lv.archivable() : false;
      attachment.archivabilityError = lv && _.isFunction(lv.archivabilityError) ? lv.archivabilityError() : null;
      attachment.sendToArchive = ko.observable(false);
      attachment.convertableToPdfA = lv && _.isFunction(lv.archivable) ? !lv.archivable() && lv.contentType() === "application/pdf" : false;
      return attachment;
    });
  };

  var model = function(params) {
    var self = this;
    self.attachments = params.application.attachments;
    var appDocId = params.application.id() + "-application";
    var caseFileDocId = params.application.id() + "-case-file";

    var docs = [
      {documentNameKey: "applications.application", metadata: params.application.metadata, id: appDocId, previewAction: "pdf-export"},
      {documentNameKey: "caseFile.heading", metadata: params.application.processMetadata, id: caseFileDocId, previewAction: "pdfa-casefile"}
    ];

    var mainDocuments = ko.pureComputed(function() {
      return addAdditionalFieldsToAttachments(docs);
    });
    self.stateMap = ko.pureComputed(function() {
      var getState = function(doc) {
        if (util.getIn(doc, ["metadata", "tila"])) {
          return ko.unwrap(ko.unwrap(doc.metadata).tila);
        } else {
          return "valmis";
        }
      };
      var stateMap = _.mapValues(_.keyBy(self.attachments(), function(att) {
        return ko.unwrap(att.id);
      }), getState);
      stateMap[appDocId] = getState(params.application);
      stateMap[caseFileDocId] = util.getIn(params.application, ["processMetadata", "tila"]) ? params.application.processMetadata().tila() : "valmis";
      return stateMap;
    });

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
      return getGroupList(filterByArchiveStatus(preAttachments(), false), buildings);
    });
    self.notArchivedPostGroups = ko.pureComputed(function() {
      return getGroupList(filterByArchiveStatus(postAttachments(), false), buildings);
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
        if (attachment.archivable && attachment.metadata().tila() !== "arkistoitu") {
          attachment.sendToArchive(true);
        }
      };
      _.forEach(archivedPreAttachments(), selectIfArchivable);
      _.forEach(archivedPostAttachments(), selectIfArchivable);
      _.forEach(self.archivedDocuments(), function(doc) {
        if (!doc.metadata().tila || doc.metadata().tila() !== "arkistoitu") {
          doc.sendToArchive(true);
        }
      });
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
      LUPAPISTE.ModalDialog.showDynamicOk(loc("application.tosMetadataWasResetTitle"), loc("application.tosMetadataWasReset"));
      var data = {
        id: ko.unwrap(params.application.id),
        functionCode: self.newTosFunction(),
        reason: self.tosFunctionCorrectionReason()
      };
      ajax
        .command("force-fix-tos-function-for-application", data)
        .success(function() {
          self.tosFunctionCorrectionReason(null);
          repository.load(ko.unwrap(params.application.id), null, function(newApplication) {
            docs[0].metadata(ko.mapping.fromJS(newApplication.metadata));
            docs[1].metadata(ko.mapping.fromJS(newApplication.processMetadata));
          });
        })
        .call();
    };

    self.convertToPdfA = function(attachment) {
      var id = ko.unwrap(attachment.id);
      openAttachmentIds.push(id);
      ajax
        .command("convert-to-pdfa", {id: ko.unwrap(params.application.id), attachmentId: id})
        .success(function() {
          repository.load(ko.unwrap(params.application.id));
        })
        .call();
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
