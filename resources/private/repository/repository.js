var repository = (function() {
  "use strict";

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

  function calculateAttachmentStateIndicators(attachment) {
    attachment.signed = false;
    var versionsByApplicants = _(attachment.versions || []).filter(function(v) {return v.user.role === "applicant";}).value();
    if (versionsByApplicants && versionsByApplicants.length) {
      var lastVersionByApplicant = _.last(versionsByApplicants).version;
      if (_.find(attachment.signatures || [], function(s) {return _.isEqual(lastVersionByApplicant, s.version);})) {
        attachment.signed = true;
      }
    }
  }

  function load(id, pending, callback) {
    var loadingApp = ajax
      .query("application", {id: id})
      .pending(pending)
      .error(function(e) {
        error("Application " + id + " not found", e);
        LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
      })
      .call();
    $.when(loadingSchemas, loadingApp).then(function(schemasResponse, loadingResponse) {
      var schemas = schemasResponse[0].schemas,
          loading = loadingResponse[0],
          application = loading.application;

      function setSchema(doc) {
        var schemaInfo = doc["schema-info"];
        var schema = findSchema(schemas, schemaInfo.name, schemaInfo.version);
        doc.schema = schema;
        doc.schema.info = _.merge(schemaInfo, doc.schema.info);
      };

      function setOperation(application, doc) {
        var schemaInfo = doc["schema-info"];
        if (schemaInfo.op) {
          var op = _.findWhere(application.operations, {id: schemaInfo.op.id});
          if (op) {
            schemaInfo.op = op;
          }
        }
      };

      if (application) {
        _.each(application.documents || [], function(doc) {
          setOperation(application, doc);
          setSchema(doc);
        });
        _.each(application.tasks || [], setSchema);
        _.each(application.comments || [], function(comment) {
          if (comment.target && comment.target.type === 'attachment' && comment.target.id) {
            var targetAttachment = _.find(application.attachments || [], function(attachment) {
              return attachment.id === comment.target.id;
            });
            if (targetAttachment) {
              comment.target.attachmentType = loc(['attachmentType', targetAttachment.type['type-group'], targetAttachment.type['type-id']]);
              comment.target.attachmentId = targetAttachment.id;
            }
          }
        });
        _.each(application.attachments ||[], calculateAttachmentStateIndicators);
        hub.send("application-loaded", {applicationDetails: loading});
        if (_.isFunction(callback)) {
          callback(application);
        }
      };
    });
  }

  function loaded(pages, f) {
    if (!_.isFunction(f)) throw "f is not a function: f=" + f;
    hub.subscribe("application-loaded", function(e) {
      if (_.contains(pages, pageutil.getPage())) {
        //TODO: passing details as 2nd param due to application.js hack (details contains the municipality persons)
        f(e.applicationDetails.application, e.applicationDetails);
      }
    });
  }

  function showApplicationList() {
    pageutil.hideAjaxWait();
    window.location.hash = "!/applications";
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
