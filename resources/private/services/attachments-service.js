//
// Provides services for attachments tab.
//
//

LUPAPISTE.AttachmentsService = function() {
  "use strict";
  var self = this;
  ko.options.deferUpdates = true;
  self.APPROVED = "ok";
  self.REJECTED = "requires_user_action";
  self.REQUIRES_AUTHORITY_ACTION = "requires_authority_action";
  self.JOB_RUNNING = "running";
  self.JOB_PENDING = "pending";
  self.JOB_WORKING = "working";
  self.JOB_DONE = "done";
  self.JOB_ERROR = "error";
  self.JOB_TIMEOUT = "timeout";
  self.serviceName = "attachmentsService";

  self.attachments = ko.observableArray([]).extend({deferred: true});
  self.authModels = ko.observable({});

  self.tagGroups = ko.observableArray([]);
  var tagGroupSets = {};

  // Filters are represented as nested arrays and are interpreted into logical rules as follows:
  // [[A B] [C D]] = (and (or A B) (or C D))
  self.filters = ko.observableArray([]);
  var filterSets = {};

  self.authModel = lupapisteApp.models.applicationAuthModel;
  self.processing = lupapisteApp.models.application.processing;
  self.applicationId = function() {
    // For some views (e.g., pate-verdict) the application model has
    // not necessarily been initialized.
    return lupapisteApp.models.application.id()
        || pageutil.hashApplicationId();
  };
  self.isArchivingProject = lupapisteApp.models.application.isArchivingProject;
  self.applicationModel = lupapisteApp.models.application;

  var reload = function() {
    self.attachments([]);
    self.queryAll();
    self.groupTypes.reset();
  };

  hub.subscribe("application-model-updated", reload);
  hub.subscribe(self.serviceName + "::updateAll", reload);

  hub.subscribe( "contextService::leave", function() {
    clearData();
    self.internedObservables = {};
    self.authModel.setData({});
  });

  function clearData() {
    disposeItems( self.attachments );
    disposeItems(tagGroupSets);
    disposeItems(filterSets);
    self.attachments([]);
    self.attachmentTypes([]);
    self.attachmentTypes.reset();
    filterSets = {};
    self.tagGroups([]);
    tagGroupSets = {};
    self.groupTypes([]);
    self.groupTypes.reset();
  }

  function queryData(queryName, responseJsonKey, dataSetter, params, hubParams) {
    if (self.authModel.ok(queryName)) {
      var queryParams = _.assign({"id": self.applicationId()}, params);
      ajax.query(queryName, queryParams)
        .success(function(data) {
          dataSetter(data[responseJsonKey]);
          hub.send( self.serviceName + "::query", _.merge({query: queryName,
                                                           key: responseJsonKey},
                                                          params,
                                                          _.omit(hubParams, "eventType")));
        })
        .onError("error.unauthorized", notify.ajaxError)
        .call();
      return true;
    } else {
      return false;
    }
  }

  // Initialize self.authModels for attachments. Creates new authorization models or reuses previously created ones.
  function initAuthModels(attachments) {
    self.authModels(_(attachments)
                    .keyBy("id")
                    .mapValues(function(attachment, id) {
                      return self.authModels()[id] || authorization.create();
                    })
                    .value());
  }

  // Refresh all authModels at once.
  function refreshAllAuthModels() {
    self.authModel.refresh({id: self.applicationId()});
    authorization.refreshModelsForCategory(self.authModels(), self.applicationId(), "attachments");
  }

  // Refresh authModel for single attachment.
  // This function should NOT be used for refreshing entire set of authModels since
  // one query is produced for each authModel.
  function refreshAuthModel(attachment) {
    var authModel = self.authModels()[attachment.id];
    if (authModel) {
      authModel.refresh(self.applicationId(),
                        {attachmentId: attachment.id,
                         fileId: util.getIn(attachment, ["latestVersion", "fileId"])});
    }
  }

  // Returns authorization model for attachment from self.authModels or creates new authModel and stores it in self.authModels.
  self.getAuthModel = function(attachmentId) {
    var authModel = self.authModels()[attachmentId];
    if (!authModel) {
      authModel = authorization.create();
      self.authModels(_.set(self.authModels(), attachmentId, authModel));
    }
    return authModel;
  };

  // Since not all attachment changes update the modified timestamp
  // (e.g., metadata changes), we keep a separate tally on when the
  // attachment may have changed (or is 'touched'). The timestamp is
  // updated on every update and query.
  function touch( attachment ) {
    var a = ko.unwrap( attachment );
    if( a ) {
      _.set( a, "__touched", new Date().getTime() );
    }
    return attachment;
  }

  function buildAttachmentModel(attachment, attachmentObs) {
    if (ko.isObservable(attachmentObs)) {
      attachmentObs(attachmentObs().reset(attachment));
    } else {
      attachmentObs = ko.observable(new LUPAPISTE.AttachmentModel(attachment, self.getAuthModel(attachment.id)));
    }
    return touch( attachmentObs );
  }

  function disposeItems( models ) {
    _.forEach( ko.unwrap(models), function( m ) {
      ko.unwrap( m ).dispose();
    });
  }

  self.setAttachments = function(attachments) {
    disposeItems( self.attachments );
    initAuthModels(attachments);
    refreshAllAuthModels();
    self.attachments(_.map(attachments, buildAttachmentModel));
  };

  self.setAttachmentData = function(attachmentData) {
    var existingAttachmentModel = self.getAttachment(attachmentData.id);
    var attachmentModel = buildAttachmentModel(attachmentData, existingAttachmentModel);
    if (!existingAttachmentModel) {
      self.authModels(_.set(self.authModels(), attachmentData.id, attachmentModel().authModel));
      self.attachments.push(attachmentModel);
    }
    refreshAuthModel(attachmentData);
  };

  self.setTagGroups = function(data) {
    self.tagGroups(data);
    _.forEach(tagGroupSets, function(tagGroupSet) {
      tagGroupSet.setTagGroups(data);
    });
  };

  self.setFilters = function(data) {
    self.filters(data);
    _.forEach(filterSets, function(filterSet) {
      filterSet.setFilters(data);
    });
  };

  self.queryAttachments = function() {
    queryData("attachments", "attachments", self.setAttachments);
  };

  self.queryTagGroupsAndFilters = function() {
    queryData("attachments-tag-groups", "tagGroups", self.setTagGroups);
    queryData("attachments-filters", "attachmentsFilters", self.setFilters);
  };

  self.queryAll = function(hubParams) {
    _.forEach(filterSets, function(filterSet) { filterSet.resetForcedVisibility(); });
    queryData("attachments", "attachments", self.setAttachments, null, hubParams);
    self.queryTagGroupsAndFilters();
  };

  // hubParams are attached to the hub send event for attachment query.
  self.queryOne = function(attachmentId, hubParams) {
    (_.get(ko.unwrap(self.getAttachment(attachmentId)), "processing", _.noop))(true);
    queryData("attachment", "attachment", self.setAttachmentData, {"attachmentId": attachmentId}, hubParams);
  };

  self.getAttachment = function(attachmentId) {
    return _.find(self.attachments(), function(attachment) {
      return attachment.peek().id === attachmentId;
    });
  };

  self.getFilters = function(filterSetId) {
    if (!filterSets[filterSetId]) {
      filterSets[filterSetId] = new LUPAPISTE.AttachmentsFilterSetModel(self.filters());
    }
    return filterSets[filterSetId];
  };

  self.getTagGroups = function(groupSetId) {
    if (!tagGroupSets[groupSetId]) {
      tagGroupSets[groupSetId] = new LUPAPISTE.AttachmentsGroupSetModel(self.tagGroups());
    }
    return tagGroupSets[groupSetId];
  };

  self.hasAttachments = ko.pureComputed(function() {
    var attachments = self.attachments();
    return _.some(attachments, function(a) {return !_.isEmpty(util.getIn(a, ["versions"]));});
  });

  // Finds an attachment id, whose index in the self.attachments is
  // the index of attachmentId + steps. See nextAttachmentId and
  // previousAttachmentId below. If the attachmentId is not found or
  // the resulting index would be out of bounds, returns null.
  function findAttachmentId( attachmentId, steps ) {
    var atts = self.attachments();
    var index = _.findIndex( atts,
                             function( a ) {
                               return a().id === attachmentId;
                             });
    var newIndex = index + steps;
    return index >= 0 && newIndex >= 0 && newIndex < _.size( atts )
      ? atts[newIndex]().id
      : null;
  }

  self.nextAttachmentId = _.partialRight( findAttachmentId, 1 );
  self.previousAttachmentId = _.partialRight( findAttachmentId, -1 );

  self.queryGroupTypes = function() {
    if (!queryData("attachment-groups", "groups", self.groupTypes)) {
      hub.subscribe("application-model-updated", function() {
        self.groupTypes.reset();
      }, true);
    }
  };

  self.groupTypes = ko.observableArray().extend({autoFetch: {fetchFn: self.queryGroupTypes}});

  hub.subscribe("op-description-changed", self.groupTypes.reset);

  self.queryAttachmentTypes = function() {
    if (!queryData("attachment-types", "attachmentTypes", self.attachmentTypes, {"lang": loc.currentLanguage})) {
      hub.subscribe("application-model-updated", function() {
        self.attachmentTypes.reset();
      }, true);
      hub.subscribe("accordionService::saveIdentifier", function() {
        self.attachmentTypes.reset();
      }, true);
    }
  };

  self.attachmentTypes = ko.observableArray().extend({autoFetch: {fetchFn: self.queryAttachmentTypes}});

  self.attachmentTypeGroups = ko.pureComputed(function() {
    return _(self.attachmentTypes()).map("type-group").uniq().value();
  });

  self.attachmentBackendIds = ko.pureComputed(function() {
    return self.applicationModel.kuntalupatunnukset();
  });

  function sendHubNotification(eventType, commandName, params, response) {
    hub.send(self.serviceName + "::" + eventType, _.merge({commandName: commandName}, params, response));
  }

  self.removeAttachment = function(attachmentId, hubParams) {
    var params = {id: self.applicationId(), attachmentId: attachmentId};
    ajax.command("delete-attachment", params)
      .success(function(res) {
        disposeItems( self.attachments.remove(function(attachment) {
          return attachment().id === attachmentId;
        }));
        self.authModel.refresh({id: self.applicationId()});
        self.queryTagGroupsAndFilters();
        sendHubNotification("remove", "delete-attachment", _.merge(params, hubParams), res);
      })
      .error(_.partial(sendHubNotification, "remove", "delete-attachment", _.merge(params, hubParams)))
      .processing(self.processing)
      .call();
    return false;
  };

  self.pollJobStatusFinished = function(status) {
    return !_.includes([self.JOB_RUNNING, self.JOB_PENDING, self.JOB_WORKING], ko.unwrap(status));
  };

  self.contentsData = function( attachmentType ) {
    var metadata = attachmentType.metadata || {};
    var list  = _.map( metadata.contents,
                       function( id ) {
                         return loc( "attachments.contents." + id);
                       });
    return {
      defaultValue: _.size( list ) <= 1
        ? _.first( list ) || attachmentType.title
        : "",
      list: list
    };
  };

  var retryTimeouts = [7*1000, 7*1000, 7*1000, 7*1000,
                       7*1000, 7*1000, 7*1000, 7*1000,
                       10*1000, 10*1000]; // total 76 secs

  function pollTimeout(statuses) {
    warn("Timeout from bind-attachment(s), fileIds: " + _.join(_.keys(statuses), ","));
    _.forEach(statuses, function(status, fileId) {
      var oldStatus = status();
      status(self.JOB_TIMEOUT);
      hub.send(self.serviceName + "::bind-attachments-status",
               {fileId: fileId,
                status: oldStatus, // It is possible that the file in hand has been successful, although overall job timed out
                jobStatus: self.JOB_TIMEOUT});
    });
  }

  function pollBindJob(statuses, attachments, previousJob, timeouts , response) {
    var timeout = _.head(timeouts);
    if (response.result === "update") {
      var job = response.job;
      _.forEach( _.values(job.value), function(fileData) {
        if ( statuses[fileData.fileId] ) {
          statuses[fileData.fileId](fileData.status);
          hub.send(self.serviceName + "::bind-attachments-status",
                   {fileId: fileData.fileId,
                    status: fileData.status,
                    jobStatus: job.status,
                    applicationId: self.applicationId});
        }
      });
      if ( job.status === self.JOB_RUNNING ) {
        var jobData = {jobId: job.id,
                       version: job.version,
                       timeout: timeout};
        ajax.query("bind-attachments-job", jobData)
          .success(_.partial(pollBindJob, statuses, attachments, jobData, _.clone(retryTimeouts)))
          .call();
      } else { // self.JOB_DONE
        _.forEach(statuses, function(status) {
          if (status === self.JOB_RUNNING) {
            status(self.JOB_TIMEOUT);
          }
        });
        self.authModel.refresh({id: self.applicationId()});
        if ( attachments.length === 1 && attachments[0].attachmentId ) {
          self.queryOne(attachments[0].attachmentId);
          self.queryTagGroupsAndFilters();
        } else {
          self.queryAll();
        }
      }
    } else { // timeout
      if (timeout) { // timeouts left, retry and decrease amount of retries
        var timeoutedJob = {jobId: previousJob.jobId,
                            version: previousJob.version,
                            timeout: timeout};
        _.delay(function() {
          ajax.query( "bind-attachments-job", timeoutedJob)
          .success(_.partial(pollBindJob, statuses, attachments, timeoutedJob, _.tail(timeouts)))
          .call();
        }, 1000);

      } else {
        pollTimeout(statuses);
      }
    }

  }

  function startBindPolling(statuses, attachments, response) {
    var jobData = { jobId: response.job.id, version: response.job.version};
    ajax.query( "bind-attachments-job", jobData)
      .success( _.partial(pollBindJob, statuses, attachments, jobData, _.clone(retryTimeouts)) )
      .call();
  }

  hub.subscribe("attachmentsService::bindJobInitialized", function(job) {
    if (job) {
      var jobStatuses = _.mapValues(job.value, function(file) { return ko.observable(file.status); });
      var attachments = job.value;
      startBindPolling(jobStatuses, attachments, {job: job});
    }
  });

  /**
   * Bind files into application attachments.
   *
   * @param attachments [BindableFile] (see bind.clj)
   * @param password password if signing attachments
   * @returns jobStatuses object, where fileId is key and job status observable is value
   */
  self.bindAttachments = function(attachments, password) {
    var jobStatuses = _(attachments).map(function(attachment) { return [attachment.fileId, ko.observable(self.JOB_RUNNING)]; }).fromPairs().value();
    ajax.command( "bind-attachments",
                  _.merge( { id: self.applicationId(),
                             filedatas: attachments },
                           _.some(attachments, "sign")  ? {password: password} : {} ))
      .processing( self.processing )
      .success( _.partial(startBindPolling, jobStatuses, attachments) )
      .call();

    return jobStatuses;
  };

  self.bindAttachment = function(attachmentId, fileId) {
    var jobStatuses = _.set({}, fileId, ko.observable(self.JOB_RUNNING));
    ajax.command( "bind-attachment", { id: self.applicationId(),
                                       attachmentId: attachmentId,
                                       fileId: fileId })
      .processing( self.processing )
      .success( _.partial(startBindPolling, jobStatuses, [{attachmentId: attachmentId}]) )
      .call();

    return jobStatuses[fileId];
  };

  self.copyUserAttachments = function(hubParams) {
    var params = {id: self.applicationId()};
    ajax.command("copy-user-attachments-to-application", params)
      .success(function(res) {
        self.authModel.refresh({id: self.applicationId()});
        self.queryAll();
        sendHubNotification("copy-user-attachments", "copy-user-attachments-to-application", _.merge(params, hubParams), res);
      })
      .error(_.partial(sendHubNotification, "copy-user-attachments", "copy-user-attachments-to-application", _.merge(params, hubParams)))
      .processing(self.processing)
      .call();
  };

  self.updateAttachment = function(attachmentId, commandName, params, hubParams) {
    var commandParams = _.assign({"id": self.applicationId(),
                                  "attachmentId": attachmentId},
                                 params);
    var attachment = ko.unwrap(self.getAttachment(attachmentId));
    ajax.command(commandName, commandParams)
      .success(function(response) {
        util.showSavedIndicator(response);
        var attachment = ko.unwrap(touch(self.getAttachment(attachmentId)));
        if (attachment) { attachment.modified(new Date().getTime()); }
        self.authModel.refresh({id: self.applicationId()});
        sendHubNotification("update", commandName, _.merge(commandParams, hubParams), response);
      })
      .error(function(response) {
        sendHubNotification("update", commandName, _.merge(commandParams, hubParams), response);
        error("Unable to update attachment: " , _.assign({commandName: commandName, commandParams: commandParams}, response));
        notify.ajaxError(response);
      })
      .processing(attachment.processing)
      .call();
  };

  self.removeAttachmentVersion = function(attachmentId, fileId, originalFileId, hubParams) {
    self.updateAttachment(attachmentId, "delete-attachment-version", {fileId: fileId, originalFileId: originalFileId}, hubParams);
  };

  self.approveAttachment = function(attachmentId, hubParams) {
    var attachment = self.getAttachment(attachmentId);
    self.updateAttachment(attachmentId, "approve-attachment", {"fileId": util.getIn(attachment, ["latestVersion", "fileId"])}, hubParams);
    self.rejectAttachmentNoteEditorState( null );
  };

  self.resetAttachment = function(attachmentId, hubParams) {
    var attachment = self.getAttachment(attachmentId);
    self.updateAttachment(attachmentId, "reset-attachment", {"fileId": util.getIn(attachment, ["latestVersion", "fileId"])}, hubParams);
    self.rejectAttachmentNoteEditorState( null );
  };


  self.rejectAttachment = function(attachmentId, hubParams) {
    var attachment = self.getAttachment(attachmentId);
    self.updateAttachment(attachmentId, "reject-attachment", {"fileId": util.getIn(attachment, ["latestVersion", "fileId"])}, hubParams);
    self.rejectAttachmentNoteEditorState( attachmentId );
  };

  self.rejectAttachmentNote = function(attachmentId, note, hubParams) {
    var attachment = self.getAttachment(attachmentId);
    self.updateAttachment(attachmentId,
                          "reject-attachment-note",
                          {fileId: util.getIn(attachment, ["latestVersion", "fileId"]),
                           note: note || ""},
                          hubParams);
    self.rejectAttachmentNoteEditorState( null );
  };

  hub.subscribe( "attachmentsService::update", function( event ) {
    if( event.commandName === "reject-attachment-note" || event.commandName === "convert-to-pdfa" ) {
      self.queryOne( event.attachmentId );
    }
  });

  // Used by reject-note component.
  self.rejectAttachmentNoteEditorState = ko.observable();

  self.setNotNeeded = function(attachmentId, flag, hubParams) {
    _.forEach(filterSets, function(filterSet) { filterSet.forceVisibility(attachmentId); });
    // Updated directly in order to propagate the changes to `attachments_view.cljs`.
    var a = self.getAttachment( attachmentId );
    if( ko.isObservable( a ) ) {
      a().notNeeded( Boolean( flag ));
    }
    self.updateAttachment(attachmentId, "set-attachment-not-needed", {"notNeeded": Boolean(flag)}, hubParams);
  };

  self.setVisibility = function(attachmentId, visibility, hubParams) {
    self.updateAttachment(attachmentId, "set-attachment-visibility", {"value": visibility}, hubParams);
  };

  self.setConstructionTime = function(attachmentId, value, hubParams) {
    self.updateAttachment(attachmentId, "set-attachment-as-construction-time", {"value": value}, hubParams);
  };

  self.setMeta = function(attachmentId, metadata, hubParams) {
    self.updateAttachment(attachmentId, "set-attachment-meta", {meta: metadata}, hubParams);
  };

  self.setForPrinting = function(attachmentId, isForPrinting, hubParams) {
    var params = {selectedAttachmentIds: isForPrinting ? [attachmentId] : [],
                  unSelectedAttachmentIds: isForPrinting ? [] : [attachmentId]};
    self.updateAttachment(attachmentId, "set-attachments-as-verdict-attachment", params, hubParams);
  };

  self.rotatePdf = function(attachmentId, rotation, hubParams) {
    self.updateAttachment(attachmentId, "rotate-pdf", {rotation: rotation}, hubParams);
  };

  self.setType = function(attachmentId, type, hubParams) {
    self.updateAttachment(attachmentId, "set-attachment-type", {attachmentType: type}, hubParams);
  };

  self.createAttachmentTemplates = function(types, group, hubParams) {
    var params =  {id: self.applicationId(), attachmentTypes: types, group: group};
    ajax.command("create-attachments", params)
      .success(function(res) {
        self.queryAll();
        sendHubNotification("create", "create-attachments", _.merge(params, hubParams), res);
      })
      .error(_.partial(sendHubNotification, "create", "create-attachments", _.merge(params, hubParams)))
      .processing(self.processing)
      .call();
  };

  self.getDefaultGroupingForType = function(type) {
    var operations = _.filter(self.groupTypes(), ["groupType", "operation"]);
    if (util.getIn(type, ["metadata", "multioperation"]) && !_.isEmpty(operations)) {
      return { groupType: "operation",
               operations: operations };
    } else if (util.getIn(type, ["metadata", "grouping"]) === "operation" && !_.isEmpty(operations)) {
      return { groupType: "operation",
               operations: [_.first(operations)] };
    } else {
      return _.pick(_.find(self.groupTypes(), ["groupType", util.getIn(type, ["metadata", "grouping"])]), "groupType");
    }
  };

  self.convertToPdfA = function(attachmentId) {
    self.updateAttachment(attachmentId, "convert-to-pdfa", {}, {});
  };

  self.getDefaultBackendId = function() {
    return _.first(self.applicationModel.kuntalupatunnukset());
  };

  function downloadRedirect( uri ) {
    if( location ) {
      location.assign( uri );
    }
  }

  self.downloadAttachments = function(attachmentIds) {
    var ids = attachmentIds || _(self.attachments()).map(ko.unwrap).map("id");
    var applicationId = self.applicationId();
    var uri = "/api/raw/download-attachments?id=" + applicationId + "&ids=" + ids.join(",") + "&lang=" + loc.getCurrentLanguage();
    downloadRedirect( uri );
  };

  function downloadAllAttachments() {
    downloadRedirect("/api/raw/download-all-attachments?id=" + self.applicationId());
  }

  hub.subscribe( self.serviceName + "::downloadAllAttachments", downloadAllAttachments );




  // If fileId is not given, the approval for the latestVersion is returned.
  // The fileId can be either fileId or originalFileId.
  self.attachmentApproval = function ( attachment, fileId ) {
    if( fileId ) {
      var version = _.find( util.getIn(attachment, ["versions"] ),
                            function( v ) {
                              return v.fileId === fileId
                                || v.originalFileId === fileId;
                            });
      fileId = _.get( version, "originalFileId");
    } else {
      fileId = util.getIn( attachment, ["latestVersion", "originalFileId"]);
    }
    return fileId && util.getIn( attachment, ["approvals", fileId]);
  };

  self.stampingData = function ( attachment ) {
    return {
        isStamped: util.getIn(attachment,["latestVersion", "stamped"]),
        user: {
            firstName: util.getIn(attachment, ["latestVersion", "user", "firstName"]),
            lastName: util.getIn(attachment, ["latestVersion", "user", "lastName"])
        },
        timestamp: util.getIn(attachment, ["latestVersion", "created"])
    };
  };

  function attachmentState( attachment ) {
    return _.get( self.attachmentApproval( attachment), "state");
  }

  //helpers for checking relevant attachment states
  self.isApproved = function(attachment) {
    return attachmentState(attachment ) === self.APPROVED;
  };

  self.isRejected = function(attachment ) {
    return attachmentState(attachment) === self.REJECTED
      && !self.isNotNeeded( attachment );
  };

    self.isNeutral = function(attachment) {
      return !( self.isApproved( attachment) || self.isRejected( attachment ));
  };


  self.isNotNeeded = function(attachment) {
    return util.getIn(attachment, ["notNeeded"]) === true;
  };
  self.requiresAuthorityAction = function(attachment) {
    return attachmentState(attachment) === self.REQUIRES_AUTHORITY_ACTION;
  };

  self.isStamped = function(attachment) {
    return util.getIn(attachment, ["latestVersion", "stamped"]) === true;
  };

  self.isResellable = function(attachment) {
    return util.getIn(attachment, ["metadata", "myyntipalvelu"]) === true;
  };

  self.toggleResell = function(attachment) {
    ajax
      .command("set-myyntipalvelu-for-attachment",
        {
          id: self.applicationId(),
          attachmentId: attachment.id,
          myyntipalvelu: !self.isResellable(attachment)
        })
      .success(function() {
        self.queryOne(attachment.id);
      })
      .call();
  };

  // True if the attachment is needed but does not have file yet.
  self.isMissingFile = function( attachment ) {
    return !self.isNotNeeded( attachment )
      && _.isEmpty( util.getIn( attachment, ["versions"]) );
  };

  //
  // Filtering attachments
  //

  self.isFiltered = function(activeFilters, attachment) {
    var att = ko.unwrap(attachment);
    return _.every(activeFilters, function(active_tags) {
      return _.isEmpty(active_tags) || !_.isEmpty(_.intersection(active_tags, att.tags));
    });
  };

  // Active filters are represented as nested arrays.
  // Attachment tags should match into filter by a logical rule interpreted from the filter:
  // [[A B] [C D]] = (and (or A B) (or C D))
  self.applyFilters = function(attachments, activeFilters) {
    return _.filter(attachments, _.partial(self.isFiltered, activeFilters));
  };

  ko.options.deferUpdates = false;

  //
  // Missing required attachments
  //

  function extractMissingAttachments(attachments) {
    var missingAttachments = _.filter(attachments, function(a) {
      var required  = util.getIn( a, ["required"]);
      var notNeeded = util.getIn( a, ["notNeeded"]);
      var noVersions = _.isEmpty( util.getIn( a, ["versions"]));
      return required && !notNeeded && noVersions;
    });
    missingAttachments = _.groupBy(missingAttachments,
                                   _.partialRight(  util.getIn,
                                                   ["type", "type-group"]));
    missingAttachments = _.map(_.keys(missingAttachments), function(k) {
      return [k, missingAttachments[k]];
    });
    return missingAttachments ;
  }

  self.missingRequiredAttachments = ko.pureComputed( function() {
    return extractMissingAttachments( self.attachments());
  });

  hub.subscribe("assignmentService::assignmentCompleted", function() {
    self.queryTagGroupsAndFilters();
  });

  hub.subscribe({eventType: "attachments-signed", currentPage: "application"}, function() {
    self.queryAll();
  });

  // Convience functions mostly for ClojureScript's benefit

  // Attachments as plain JS and function properties removed.
  // AuthModel is replaced with auth flags: can-delete? (for Pate attachment list)

  function rawHelper ( a ) {
    return _.omitBy( a, function( v, k ) {
      return _.isFunction( v ) || k === "authModel";
    });
  }

  self.rawAttachments = function() {
    return _.map( ko.mapping.toJS( self.attachments ),
                  function( a ) {
                    return _.merge( rawHelper( a ),
                                    {"can-delete?": self.getAuthModel(a.id).ok( "delete-attachment")});
                  });
  };

  self.rawAttachment = function( attachmentId ) {
    var a = self.getAttachment( attachmentId );
    return a && rawHelper( ko.mapping.toJS( a ));
  };

  // Notify when attachments or auth models have changed
  var notifyChanged = _.debounce( _.wrap( self.serviceName + "::changed", hub.send),
                                  100);
  self.attachments.subscribe( notifyChanged );
  hub.subscribe( "category-auth-model-changed",
                 function( event ) {
                   if (event.category === "attachments") {
                     notifyChanged();
                   }
                 });

  // Returns id - __touched object for the current attachments.
  self.attachmentTimestamps = function() {
    return _.reduce( self.attachments.peek(),
                     function( acc, a ) {
                       acc[a.peek().id] =  a.peek().__touched;
                       return acc;
                     },
                     {}
                   );
  };

};
