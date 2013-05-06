var locationSearch = (function() {

  var serchPointByAddress = function(address, onSuccess, onFail) {
    ajax
      .get("/proxy/get-address")
      .param("query", address)
      .success(onSuccess)
      .fail(onFail)
      .call();
    return self;
  };

  var searchPointByPropertyId = function(propertyId, onSuccess, onFail) {
    ajax
      .get("/proxy/point-by-property-id")
      .param("property-id", util.prop.toDbFormat(propertyId))
      .success(onSuccess)
      .fail(onFail)
      .call();
    return self;
  };

  var searchPropertyId = function(x, y, onSuccess) {
    ajax
      .get("/proxy/property-id-by-point")
      .param("x", x)
      .param("y", y)
      .success(onSuccess)
      .call();
    return self;
  };

  var searchAddress = function(x, y, onSuccess) {
    ajax
      .get("/proxy/address-by-point")
      .param("x", x)
      .param("y", y)
      .success(onSuccess)
      .call();
    return self;
  };

  return {
    pointByAddress: serchPointByAddress,
    pointByPropertyId: searchPointByPropertyId,
    propertyIdByPoint: searchPropertyId,
    addressByPoint: searchAddress
  };
})();
