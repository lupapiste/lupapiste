var locationSearch = (function() {

  var searchPropertyId = function(x, y, onSuccess, onError) {
    ajax
      .get("/proxy/property-id-by-point")
      .param("x", x)
      .param("y", y)
      .success(onSuccess)
      .error(onError)
      .call();
    return self;
  };

  var searchAddress = function(x, y, onSuccess, onError) {
    ajax
      .get("/proxy/address-by-point")
      .param("x", x)
      .param("y", y)
      .success(onSuccess)
      .error(onError)
      .call();
    return self;
  };

  return {
    propertyId: searchPropertyId,
    address: searchAddress
  };
})();
