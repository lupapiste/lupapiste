var repository = (function() {
  "use strict";

  var currentlyLoadingId = null;
  var currentQuery = null;

  var loadingSchemas = ajax
    .query("schemas")
    .error(function(e) { error("can't load schemas", e); })
    .call();

  function findSchema(schemas, name, version) {
    var v = schemas[version] || schemaNotFound(schemas, name, version);
    var s = v[name] || schemaNotFound(schemas, name, version);
    return _.clone(s);
  }

  function schemaNotFound(schemas, name, version) {
    var message = "unknown schema, name='" + name + "', version='" + version + "'";
    error(message);
  }

  function calculateAttachmentStateIndicators(attachment, application) {
    var auths = _(application.auth).filter(function(a) {return _.contains(LUPAPISTE.config.writerRoles, a.role);}).pluck("id").value();

    attachment.signed = false;
    var lastSignature = _.last(attachment.signatures || []);
    var versions = _.filter(attachment.versions);
    if (lastSignature && versions.length > 0) {
      var signedVersion = _.find(versions, function(v) {
        // Check that signed version was created before the signature
        return v.created < lastSignature.created &&
           v.version.major === lastSignature.version.major &&
           v.version.minor === lastSignature.version.minor;
      });

      var unsignedVersions = _(versions)
        // Drop previous, signed versions
        .dropWhile(function(v) {
          return v.version.major !== lastSignature.version.major || v.version.minor !== lastSignature.version.minor;
        })
        // Drop current, signed versions
        .rest()
        // Keep new versions added by applicants
        .filter(function(v) {
          return v.user.role === "applicant" ||  _.contains(auths, v.user.id);
        })
        .value();
      attachment.signed = signedVersion && unsignedVersions.length === 0;
    }

    attachment.isSent = false;
    attachment.sentDateString = "-";
    if (attachment.sent) {
      // TODO check if sent to KRYSP afterwards (not yet in transfers log)
      if (!_.isEmpty(_.where(application.transfers, {type: "attachments-to-asianhallinta"}))) {
        attachment.isSentToAsianhallinta = true;
      }
      attachment.isSent = true;
      attachment.sentDateString = moment(attachment.sent).format("D.M.YYYY");
    }

    attachment.stamped = attachment.latestVersion ? attachment.latestVersion.stamped : false;
  }

  function setAttachmentOperation(operations, attachment) {
    if (attachment.op) {
      var op = _.findWhere(operations, {id: attachment.op.id});
      if (op) {
        attachment.op = op;
      } else {
        attachment.op = null;
      }
    }
  }

  function getAllOperations(application) {
    return _.filter([application.primaryOperation].concat(application.secondaryOperations), function(item) {
      return !_.isEmpty(item);
    });
  }

  function bySchemaName(schemaName) {
    return function(task) {
      return util.getIn(task, ["schema-info", "name"]) === schemaName;
    };
  }

  function tasksDataBySchemaName(tasks, schemaName, mapper) {
    return _(tasks).filter(bySchemaName(schemaName)).map(mapper).value();
  }

  function calculateVerdictTasks(verdict, tasks) {
    // Manual verdicts have one paatokset item
    if (verdict.paatokset && verdict.paatokset.length === 1) {

      var myTasks = _.filter(tasks, function(task) {
        return task.source && task.source.type === "verdict" && task.source.id === verdict.id;
      });

      var lupamaaraukset = _(verdict.paatokset || []).pluck("lupamaaraykset").filter().value();

      if (lupamaaraukset.length === 0 && myTasks.length > 0) {
        var katselmukset = tasksDataBySchemaName(myTasks, "task-katselmus", function(task) {
          return {katselmuksenLaji: util.getIn(task, ["data", "katselmuksenLaji", "value"], "muu katselmus"), tarkastuksenTaiKatselmuksenNimi: task.taskname};
        });
        var tyonjohtajat = tasksDataBySchemaName(myTasks, "task-vaadittu-tyonjohtaja", _.property("taskname"));
        var muut = tasksDataBySchemaName(myTasks, "task-lupamaarays", _.property("taskname"));

        verdict.paatokset[0].lupamaaraykset = {vaaditutTyonjohtajat: tyonjohtajat,
                                               muutMaaraykset: muut,
                                               vaaditutKatselmukset: katselmukset};
      }
    }
  }

  function loadingErrorHandler(id, e) {
    currentlyLoadingId = null;
    error("Application " + id + " not found", e);
    LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
  }

  function doLoad(id, pending, callback) {
    currentQuery = ajax
      .query("application", {id: id})
      .pending(pending || _.noop)
      .error(_.partial(loadingErrorHandler, id))
      .fail(function (jqXHR) {
        if (jqXHR && jqXHR.status > 0) {
          loadingErrorHandler(id, jqXHR);
        }
      })
      .call();


    $.when(loadingSchemas, currentQuery).then(function(schemasResponse, loadingResponse) {
      var schemas = schemasResponse[0].schemas,
          loading = loadingResponse[0],
          application = loading.application;

      function setSchema(doc) {
        var schemaInfo = doc["schema-info"];
        var schema = findSchema(schemas, schemaInfo.name, schemaInfo.version);
        doc.schema = schema;
        doc.schema.info = _.merge(schemaInfo, doc.schema.info);
      }

      function setOperation(application, doc) {
        var schemaInfo = doc["schema-info"];
        if (schemaInfo.op) {
          var op = _.findWhere(application.allOperations, {id: schemaInfo.op.id});
          if (op) {
            schemaInfo.op = op;
          }
        }
      }

      if (application) {
        if (application.id === currentlyLoadingId) {
          currentlyLoadingId = null;
          application.allOperations = getAllOperations(application);

          _.each(application.documents || [], function(doc) {
            setOperation(application, doc);
            setSchema(doc);
          });

          _.each(application.tasks || [], setSchema);

          _.each(application.comments || [], function(comment) {
            if (comment.target && comment.target.type === "attachment" && comment.target.id) {
              var targetAttachment = _.find(application.attachments || [], function(attachment) {
                return attachment.id === comment.target.id;
              });
              if (targetAttachment) {
                comment.target.attachmentType = loc(["attachmentType", targetAttachment.type["type-group"], targetAttachment.type["type-id"]]);
                comment.target.attachmentId = targetAttachment.id;
              }
            }
          });

          _.each(application.attachments ||[], function(att) {
            calculateAttachmentStateIndicators(att, application);
            setAttachmentOperation(application.allOperations, att);
          });

          _.each(application.verdicts ||[], function(verdict) {
            calculateVerdictTasks(verdict, application.tasks);
          });

          application.tags = _(application.tags || []).map(function(tagId) {
            return {id: tagId, label: util.getIn(application, ["organizationMeta", "tags", tagId])};
          }).filter("label").value();

          var sortedAttachmentTypes = attachmentUtils.sortAttachmentTypes(application.allowedAttachmentTypes);
          application.allowedAttachmentTypes = sortedAttachmentTypes;

          hub.send("application-loaded", {applicationDetails: loading});
          if (_.isFunction(callback)) {
            callback(application);
          }
        } else {
          error("Concurrent loading issue, old id = " + currentlyLoadingId);
        }
      }
    });
  }

  function load(id, pending, callback) {
    if (window.location.hash.indexOf(id) === -1) {
      // Application is not visible, do not load
      return;
    }

    if (currentlyLoadingId) {
      currentQuery.abort();
    }
    currentlyLoadingId = id;
    doLoad(id, pending, callback);
  }

  function loaded(pages, f) {
    if (!_.isFunction(f)) {
      throw "f is not a function: f=" + f;
    }
    hub.subscribe("application-loaded", function(e) {
      if (_.contains(pages, pageutil.getPage())) {
        //TODO: passing details as 2nd param due to application.js hack (details contains the municipality persons)
        f(e.applicationDetails.application, e.applicationDetails);
      }
    });
  }

  function showApplicationList() {
    pageutil.hideAjaxWait();
    pageutil.openPage("applications");
  }

  // Cannot be changed to use LUPAPISTE.ModalDialog.showDynamicYesNo, because the id is registered with hub.subscribe.
  LUPAPISTE.ModalDialog.newYesNoDialog("dialog-application-load-error",
      loc("error.application-not-found"), loc("error.application-not-accessible"),
      loc("navigation"), showApplicationList, loc("logout"), function() {hub.send("logout");});

  hub.subscribe({type: "dialog-close", id : "dialog-application-load-error"}, function() {
    showApplicationList();
  });

  return {
    load: load,
    loaded: loaded,
    schemas: loadingSchemas.promise() // for debugging
  };

})();
